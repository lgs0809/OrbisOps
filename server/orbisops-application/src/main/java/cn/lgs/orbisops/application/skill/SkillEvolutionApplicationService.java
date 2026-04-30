package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionOpportunityPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSourceSetPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application process manager for the reusable Skill Evolution candidate pipeline. */
public class SkillEvolutionApplicationService implements SkillEvolutionPipelinePort {

    private static final int PENDING_HINT_LIMIT = 50;
    private static final int AUTHORING_CANDIDATE_BUDGET = 1;

    private SkillExperienceGroupingService grouping;
    public SkillEvolutionApplicationService withGrouping(SkillExperienceGroupingService grouping) {
        this.grouping=java.util.Objects.requireNonNull(grouping);return this;
    }

    private final SkillEvolutionSignalApplicationService signalService;
    private final SkillExperienceApplicationService experienceService;
    private final SkillEvolutionAuthoringPort authoringPort;
    private final SkillEvolutionSimilarityPort similarityPort;
    private final SkillEvolutionPipelineAuditPort auditPort;
    private final SkillEvolutionOpportunityPolicy opportunityPolicy;
    private final SkillEvolutionPromotionSettings promotionSettings;
    private final SkillEvolutionPipelineMapper mapper;
    private final SkillEvolutionHintSelector hintSelector;
    private final SkillEvolutionProposalPort proposalPort;
    private final SkillEvolutionCandidateSetSelector candidateSetSelector;
    private final SkillCandidateTournamentRolloutCoordinator tournamentRolloutCoordinator;
    private final SkillEvolutionCandidateCoordinator candidateCoordinator;
    private final SkillEvolutionEvalCaseFactory evalCaseFactory;

    public SkillEvolutionApplicationService(
            SkillEvolutionSignalApplicationService signalService,
            SkillExperienceApplicationService experienceService,
            SkillPatchCandidateApplicationService candidateService,
            SkillReleaseApplicationService releaseService,
            SkillEvolutionAuthoringPort authoringPort,
            SkillEvolutionSimilarityPort similarityPort,
            SkillEvolutionPipelineAuditPort auditPort,
            SkillEvolutionPayloadCodec payloadCodecPort,
            SkillEvolutionOpportunityPolicy opportunityPolicy,
            SkillEvolutionPromotionSettings promotionSettings) {
        this(
                signalService,
                experienceService,
                candidateService,
                releaseService,
                authoringPort,
                similarityPort,
                auditPort,
                payloadCodecPort,
                opportunityPolicy,
                promotionSettings,
                new SkillEvolutionCandidateSetSelector(),
                SkillCandidateTournamentSettings.legacy());
    }

    public SkillEvolutionApplicationService(
            SkillEvolutionSignalApplicationService signalService,
            SkillExperienceApplicationService experienceService,
            SkillPatchCandidateApplicationService candidateService,
            SkillReleaseApplicationService releaseService,
            SkillEvolutionAuthoringPort authoringPort,
            SkillEvolutionSimilarityPort similarityPort,
            SkillEvolutionPipelineAuditPort auditPort,
            SkillEvolutionPayloadCodec payloadCodecPort,
            SkillEvolutionOpportunityPolicy opportunityPolicy,
            SkillEvolutionPromotionSettings promotionSettings,
            SkillEvolutionCandidateSetSelector candidateSetSelector,
            SkillCandidateTournamentSettings tournamentSettings) {
        this(signalService,experienceService,candidateService,releaseService,authoringPort,similarityPort,auditPort,
                payloadCodecPort,opportunityPolicy,promotionSettings,candidateSetSelector,tournamentSettings,null);
    }

    public SkillEvolutionApplicationService(
            SkillEvolutionSignalApplicationService signalService,SkillExperienceApplicationService experienceService,
            SkillPatchCandidateApplicationService candidateService,SkillReleaseApplicationService releaseService,
            SkillEvolutionAuthoringPort authoringPort,SkillEvolutionSimilarityPort similarityPort,
            SkillEvolutionPipelineAuditPort auditPort,SkillEvolutionPayloadCodec payloadCodecPort,
            SkillEvolutionOpportunityPolicy opportunityPolicy,SkillEvolutionPromotionSettings promotionSettings,
            SkillEvolutionCandidateSetSelector candidateSetSelector,SkillCandidateTournamentSettings tournamentSettings,
            SkillEvolutionProposalPort proposalPort) {
        if (signalService == null
                || experienceService == null
                || candidateService == null
                || releaseService == null
                || authoringPort == null
                || similarityPort == null
                || auditPort == null
                || payloadCodecPort == null
                || promotionSettings == null
                || candidateSetSelector == null
                || tournamentSettings == null) {
            throw new IllegalArgumentException(
                    "SKILL_EVOLUTION_PIPELINE_DEPENDENCY_REQUIRED");
        }
        this.signalService = signalService;
        this.experienceService = experienceService;
        this.authoringPort = authoringPort;
        this.similarityPort = similarityPort;
        this.auditPort = auditPort;
        this.opportunityPolicy = opportunityPolicy == null
                ? new SkillEvolutionOpportunityPolicy()
                : opportunityPolicy;
        this.promotionSettings = promotionSettings;
        this.mapper = new SkillEvolutionPipelineMapper(payloadCodecPort);
        this.hintSelector = new SkillEvolutionHintSelector(payloadCodecPort);
        this.proposalPort = proposalPort;
        this.candidateSetSelector = candidateSetSelector;
        this.tournamentRolloutCoordinator = new SkillCandidateTournamentRolloutCoordinator(
                candidateSetSelector,
                similarityPort,
                auditPort,
                tournamentSettings);
        this.candidateCoordinator = new SkillEvolutionCandidateCoordinator(
                candidateService,
                experienceService,
                signalService,
                releaseService,
                auditPort,
                mapper);
        this.evalCaseFactory = new SkillEvolutionEvalCaseFactory();
    }

    @Override
    public SkillEvolutionPipelineDecision decide(
            SkillEvolutionPipelineRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "SKILL_EVOLUTION_PIPELINE_REQUEST_REQUIRED");
        }
        SkillEvolutionInputSummary summary = request.summary();
        Map<String, Object> input = mapper.inputView(request);
        String opportunityType = opportunityPolicy.detect(request.opportunity());
        if ("NO_SIGNAL".equals(opportunityType)
                || "NO_REUSABLE_PATTERN".equals(opportunityType)) {
            return skipped(
                    request,
                    "NO_SIGNAL".equals(opportunityType)
                            ? "SKIP_NO_SIGNAL"
                            : "SKIP_NO_REUSABLE_PATTERN",
                    Map.of());
        }

        SkillExperienceRecordResult observed = experienceService.recordObservation(mapper.experienceInput(request, opportunityType));
        if (!"SUCCEEDED".equals(observed.observation().outcome())
                || !observed.observation().verifiedTaskOutcome().matches(request.projectId(), request.runId())) {
            return skipped(request, "SKIP_TASK_OUTCOME_UNVERIFIED", Map.of(
                    "observationId", observed.observation().observationId(), "auditOnly", true));
        }
        SkillExperienceRecordResult experience=grouping==null?observed:grouping.group(request,observed);
        SkillEvolutionSignalSnapshot signal = signalService.record(
                new SkillEvolutionSignalCommand(
                        opportunityType,
                        request.projectId(),
                        request.agentId(),
                        request.runId(),
                        request.sessionId(),
                        mapper.encode(input)));
        SkillEvolutionHintSnapshot hint = signalService.createHint(
                new SkillEvolutionHintCommand(
                        signal.signalId(),
                        request.projectId(),
                        request.runId(),
                        opportunityType,
                        mapper.encode(input)));
        int requiredObservations = promotionSettings.requiredObservations(
                opportunityType);
        int observationCount = experience.cluster().successfulCount();
        if (observationCount < requiredObservations) {
            Map<String, Object> result = mapper.skippedResult(
                    "SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS",
                    Map.of(
                            "signalId", signal.signalId(),
                            "observationId",
                            experience.observation().observationId(),
                            "clusterKey",
                            experience.observation().clusterKey(),
                            "observationCount", observationCount,
                            "requiredObservations", requiredObservations));
            result.put("signal", mapper.signalView(signal));
            result.put("hint", mapper.hintView(hint));
            result.put("experience", mapper.experienceView(experience));
            return skipped(request, result);
        }
        SkillExperienceClusterEvidence clusterEvidence =
                experienceService.clusterEvidence(
                        request.projectId(),
                        request.agentId(),
                        experience.observation().clusterKey());
        if (clusterEvidence.distinctRuns() < 3 || clusterEvidence.distinctConditions() < 2) {
            Map<String, Object> result = mapper.skippedResult(
                    "SKIP_INSUFFICIENT_SOURCE_DIVERSITY",
                    Map.of(
                            "signalId", signal.signalId(),
                            "clusterKey", experience.observation().clusterKey(),
                            "distinctTasks", clusterEvidence.distinctRuns(),
                            "requiredDistinctRuns",
                            3,
                            "distinctConditions", clusterEvidence.distinctConditions(),
                            "requiredConditions", 2,
                            "hardCaseCount",
                            clusterEvidence.hardCaseCount()));
            result.put("signal", mapper.signalView(signal));
            result.put("hint", mapper.hintView(hint));
            result.put("experience", mapper.experienceView(experience));
            return skipped(request, result);
        }
        if (!authoringPort.available()) throw new IllegalStateException("SKILL_EVOLUTION_MODEL_UNAVAILABLE");
        List<SkillExperienceConsolidationSample> consolidationSamples =
                experienceService.consolidationSamples(
                        request.projectId(),
                        request.agentId(),
                        experience.observation().clusterKey(),
                        cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy.ARCHIVE_LIMIT,request.runId());
        new SkillEvolutionSourceSetPolicy().requireUsable(consolidationSamples,request.projectId(),request.runId(),summary.sourceHash());
        Map<String,String> acceptedHashes=consolidationSamples.stream().collect(java.util.stream.Collectors.toMap(
                SkillExperienceConsolidationSample::runId,SkillExperienceConsolidationSample::sourceHash));

        List<SkillEvolutionSelectedHint> selectedHints = hintSelector.select(
                signalService.pendingHints(
                        request.projectId(),
                        PENDING_HINT_LIMIT).stream().filter(h -> request.projectId().equals(h.projectId())
                        && acceptedHashes.containsKey(h.runId())
                        && acceptedHashes.get(h.runId()).equals(hintSourceHash(h.contentJson()))).toList(),
                summary.normalizedUserGoal() + " " + summary.finalReport(),
                signal.signalId(),
                request.runId(),
                opportunityType);
        Map<String, Object> authoringInput = new LinkedHashMap<>(input);
        // The current whole task is in consolidatedExperiences; do not send its full text twice.
        authoringInput.remove("acceptedTaskEpisode");
        // Explicit procedures already appear in the verified task messages. Mutable hint text is never author input.
        authoringInput.put(
                "consolidatedExperiences",
                consolidationSamples.stream()
                        .map(this::consolidationView)
                        .toList());
        authoringInput.put(
                "hardCases",
                consolidationSamples.stream()
                        .filter(SkillExperienceConsolidationSample::hardCase)
                        .map(s->Map.of("sourceId",s.sourceId(),"sourceHash",s.sourceHash(),"runId",s.runId()))
                        .toList());
        authoringInput.putIfAbsent("defectDiagnoses", List.of());
        authoringInput.putIfAbsent("optimizationMemories", List.of());
        authoringInput.putIfAbsent("securityBoundary", Map.of(
                "productionWriteAllowed", false,
                "changePackageOnly", true));
        authoringInput.putIfAbsent("toolBoundary", Map.of(
                "executionMode", "READ_ONLY_OR_SANDBOX",
                "directLandingAllowed", false));
        authoringInput.put("baselineMetadata", Map.of(
                "projectId", request.projectId(),
                "agentId", request.agentId(),
                "runId", request.runId(),
                "contextBundleHash", summary.contextBundleHash()));
        if(proposalPort==null) throw new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_STORE_REQUIRED");
        var frozenRelated=new java.util.concurrent.atomic.AtomicReference<List<Map<String,Object>>>();
        SkillEvolutionAuthoringPort durableAuthor = finalInput -> {
            var plan=new SkillEvolutionProposalResolver(proposalPort,similarityPort)
                    .resolve(request.projectId(),request.jobClaim(),summary.sourceHash(),experience.observation().clusterKey(),finalInput);
            frozenRelated.set(cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy.references(plan.input().get("relatedSkills"),request.projectId()));
            similarityPort.validateRelatedSkills(request.projectId(),frozenRelated.get());
            if(plan.authored().isEmpty()) {
                var frozenPlan=plan;
                var generated=authoringPort.author(plan.input(),new SkillAuthoringProgressPort() {
                    public java.util.Optional<Map<String,Object>> read(int index,String inputHash) {
                        return proposalPort.batchReview(request.jobClaim(),frozenPlan,index,inputHash);
                    }
                    public Map<String,Object> save(int index,String inputHash,Map<String,Object> review) {
                        return proposalPort.saveBatchReview(request.jobClaim(),frozenPlan,index,inputHash,review);
                    }
                });
                plan=proposalPort.authored(request.jobClaim(),plan,generated.payload());
            }
            var payload=new LinkedHashMap<>(plan.authored());
            payload.put("authoringPlanId",plan.planId());payload.put("authoringPlanHash",plan.planHash());
            return SkillEvolutionAuthoredCandidate.from(payload);
        };
        List<SkillEvolutionAuthoredCandidateOption> authoredOptions =
                new SkillEvolutionMultiCandidateAuthoringService(durableAuthor).author(
                        authoringInput, AUTHORING_CANDIDATE_BUDGET);
        // Author-supplied examples remain optional development artifacts, never release admission quotas.
        List<SkillEvolutionAuthoredCandidateOption> evaluatedOptions = authoredOptions;
        SkillCandidateSetSelectionResult legacySelection = candidateSetSelector.select(
                SkillCandidateSelectionMode.LEGACY_COMPATIBILITY,
                evaluatedOptions,
                (SkillCandidateTournamentContext) null);
        SkillEvolutionAuthoredCandidate legacyAuthored = legacySelection.selectedCandidate();
        if (!legacyAuthored.reusableChange()) {
            Map<String, Object> result = mapper.skippedResult(
                    "SKIP_NO_REUSABLE_PATTERN",
                    Map.of("signalId", signal.signalId()));
            result.put("signal", mapper.signalView(signal));
            result.put("hint", mapper.hintView(hint));
            return skipped(request, result);
        }
        if (summary.evidenceReferences().isEmpty()) {
            Map<String, Object> result = mapper.skippedResult(
                    "SKIP_WAITING_FOR_TRUSTED_EVIDENCE",
                    Map.of(
                            "signalId", signal.signalId(),
                            "pendingHintCount", selectedHints.size()));
            result.put("signal", mapper.signalView(signal));
            result.put("hint", mapper.hintView(hint));
            return skipped(request, result);
        }

        SkillEvolutionSimilarityMatch similar = similarityPort.bestFrozenMatch(
                request.projectId(), legacyAuthored.payload(), frozenRelated.get());
        if (similar == null) similar = SkillEvolutionSimilarityMatch.none();
        if (similar.frozen()) {
            String matchedSkillId = similar.skillId();
            Map<String, Object> result = mapper.skippedResult(
                    "SKIP_SIMILAR_SKILL_FROZEN",
                    Map.of("matchedSkillId", matchedSkillId));
            result.put("matchedSkillId", matchedSkillId);
            result.put("similarity", similar.similarity());
            return skipped(request, result);
        }
        SkillCandidateTournamentRolloutCoordinator.Outcome rollout =
                new SkillCandidateTournamentRolloutCoordinator.Outcome(legacySelection, similar, "",
                        Map.of("publicationPolicy", SkillAutomaticPublicationService.POLICY));
        if (!rollout.blockingReason().isBlank()) {
            Map<String, Object> result = mapper.skippedResult(
                    rollout.blockingReason(), rollout.details());
            result.put("signal", mapper.signalView(signal));
            result.put("hint", mapper.hintView(hint));
            return skipped(request, result);
        }
        SkillCandidateSetSelectionResult selection = rollout.selection();
        SkillEvolutionAuthoredCandidate authored = selectionMetadata(
                selection, evaluatedOptions, rollout.details());
        SkillEvolutionSimilarityMatch effectiveSimilar = rollout.similar();
        try {
            return candidateCoordinator.create(request, opportunityType, signal, hint, experience,
                    selectedHints, authored, effectiveSimilar);
        } catch (IllegalStateException gate) {
            if (java.util.Set.of("SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES", "SKILL_EVOLUTION_INSUFFICIENT_NEW_CONDITIONS")
                    .contains(String.valueOf(gate.getMessage()))) {
                return skipped(request, "SKIP_INSUFFICIENT_NEW_SOURCE_DIVERSITY", Map.of(
                        "gate", gate.getMessage(), "signalId", signal.signalId(),
                        "requiredNewTasks", 3, "requiredNewConditions", 2));
            }
            throw gate;
        }
    }

    private SkillEvolutionAuthoredCandidate selectionMetadata(
            SkillCandidateSetSelectionResult selection,
            List<SkillEvolutionAuthoredCandidateOption> options,
            Map<String, Object> rolloutDetails) {
        SkillEvolutionAuthoredCandidate authored = selection.selectedCandidate();
        if (!authored.reusableChange()) return authored;
        Map<String, Object> selectedPayload = new LinkedHashMap<>(authored.payload());
        selectedPayload.put("candidateSetSize", options.size());
        selectedPayload.put("candidateSelectionMode", selection.mode().name());
        selectedPayload.put("candidateSelectionReason", selection.reasonCode());
        selectedPayload.put("candidateSetAudits", options.stream()
                .map(this::authoringAuditView)
                .toList());
        selectedPayload.put("candidateTournamentRollout", rolloutDetails);
        return SkillEvolutionAuthoredCandidate.from(Map.copyOf(selectedPayload));
    }

    private SkillEvolutionPipelineDecision skipped(
            SkillEvolutionPipelineRequest request,
            String reasonCode,
            Map<String, Object> details) {
        return skipped(
                request,
                mapper.skippedResult(reasonCode, details));
    }

    private SkillEvolutionPipelineDecision skipped(
            SkillEvolutionPipelineRequest request,
            Map<String, Object> result) {
        SkillEvolutionPipelineDecision decision = mapper.decision(result);
        auditPort.recordSkipped(
                request.projectId(),
                request.agentId(),
                request.runId(),
                decision.reasonCode(),
                result);
        return decision;
    }

    private Map<String, Object> authoringAuditView(
            SkillEvolutionAuthoredCandidateOption option) {
        SkillEvolutionAuthoringAudit audit = option.audit();
        return Map.of(
                "modelId", audit.modelId(),
                "promptVersion", audit.promptVersion(),
                "seed", audit.seed(),
                "inputHash", audit.inputHash(),
                "candidateBudget", audit.candidateBudget(),
                "candidateIndex", audit.candidateIndex(),
                "direction", audit.direction().name());
    }

    private Map<String, Object> consolidationView(
            SkillExperienceConsolidationSample sample) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("observationId", sample.observationId());
        view.put("runId", sample.runId());
        view.put("sessionId", sample.sessionId());
        view.put("observationType", sample.observationType());
        view.put("outcome", sample.outcome());
        view.put("taskTemplateHash", sample.taskTemplateHash());
        view.put("trajectoryHash", sample.trajectoryHash());
        view.put("summary", sample.summary());
        view.put("qualityScore", sample.qualityScore());
        view.put("hardCase", sample.hardCase());
        view.put("sourceId", sample.sourceId());
        view.put("sourceHash", sample.sourceHash());
        view.put("taskEpisodeId", sample.taskEpisodeId());
        view.put("conditionKey", sample.conditionKey());
        view.put("acceptedTaskEpisode", sample.episodeJson());
        view.put(
                "evidenceRefs",
                sample.evidenceReferences().stream()
                        .map(reference -> Map.of(
                                "evidenceId", mapper.text(reference.evidenceId()),
                                "resultId", mapper.text(reference.resultId()),
                                "outputHash", mapper.text(reference.outputHash()),
                                "sourceType", mapper.text(reference.sourceType())))
                        .toList());
        return Map.copyOf(view);
    }

    private List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private String hintSourceHash(String json) {
        try { return mapper.text(cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(json).get("evolutionSourceHash")); }
        catch (IllegalArgumentException invalidHint) { return ""; }
    }
}
