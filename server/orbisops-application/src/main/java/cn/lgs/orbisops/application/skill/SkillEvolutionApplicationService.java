package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionOpportunityPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application process manager for the reusable Skill Evolution candidate pipeline. */
public class SkillEvolutionApplicationService implements SkillEvolutionPipelinePort {

    private static final int PENDING_HINT_LIMIT = 50;
    private static final int AUTHORING_CANDIDATE_BUDGET = 3;

    private final SkillEvolutionSignalApplicationService signalService;
    private final SkillExperienceApplicationService experienceService;
    private final SkillEvolutionAuthoringPort authoringPort;
    private final SkillEvolutionSimilarityPort similarityPort;
    private final SkillEvolutionPipelineAuditPort auditPort;
    private final SkillEvolutionOpportunityPolicy opportunityPolicy;
    private final SkillEvolutionPromotionSettings promotionSettings;
    private final SkillEvolutionPipelineMapper mapper;
    private final SkillEvolutionHintSelector hintSelector;
    private final SkillEvolutionMultiCandidateAuthoringService multiCandidateAuthoringService;
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
        this.multiCandidateAuthoringService =
                new SkillEvolutionMultiCandidateAuthoringService(authoringPort);
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
        SkillExperienceRecordResult experience =
                experienceService.recordObservation(
                        mapper.experienceInput(request, opportunityType));
        int requiredObservations = promotionSettings.requiredObservations(
                opportunityType);
        int observationCount = experience.cluster().observationCount();
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
        if (!promotionSettings.sourceDiversitySatisfied(
                clusterEvidence.distinctRuns(),
                clusterEvidence.distinctSessions())) {
            Map<String, Object> result = mapper.skippedResult(
                    "SKIP_INSUFFICIENT_SOURCE_DIVERSITY",
                    Map.of(
                            "signalId", signal.signalId(),
                            "clusterKey", experience.observation().clusterKey(),
                            "distinctRuns", clusterEvidence.distinctRuns(),
                            "requiredDistinctRuns",
                            promotionSettings.minimumDistinctRuns(),
                            "distinctSessions",
                            clusterEvidence.distinctSessions(),
                            "requiredDistinctSessions",
                            promotionSettings.minimumDistinctSessions(),
                            "hardCaseCount",
                            clusterEvidence.hardCaseCount()));
            result.put("signal", mapper.signalView(signal));
            result.put("hint", mapper.hintView(hint));
            result.put("experience", mapper.experienceView(experience));
            return skipped(request, result);
        }
        List<SkillExperienceConsolidationSample> consolidationSamples =
                experienceService.consolidationSamples(
                        request.projectId(),
                        request.agentId(),
                        experience.observation().clusterKey(),
                        promotionSettings.consolidationSampleLimit());

        List<SkillEvolutionSelectedHint> selectedHints = hintSelector.select(
                signalService.pendingHints(
                        request.projectId(),
                        PENDING_HINT_LIMIT),
                summary.normalizedUserGoal() + " " + summary.finalReport(),
                signal.signalId(),
                request.runId(),
                opportunityType);
        Map<String, Object> authoringInput = new LinkedHashMap<>(input);
        authoringInput.put(
                "procedureHints",
                selectedHints.stream()
                        .map(SkillEvolutionSelectedHint::content)
                        .filter(value -> !value.isBlank())
                        .toList());
        authoringInput.put(
                "consolidatedExperiences",
                consolidationSamples.stream()
                        .map(this::consolidationView)
                        .toList());
        authoringInput.put(
                "hardCases",
                consolidationSamples.stream()
                        .filter(SkillExperienceConsolidationSample::hardCase)
                        .map(this::consolidationView)
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
        List<SkillEvolutionAuthoredCandidateOption> authoredOptions =
                multiCandidateAuthoringService.author(
                        authoringInput, AUTHORING_CANDIDATE_BUDGET);
        List<SkillEvolutionAuthoredCandidateOption> evaluatedOptions = authoredOptions.stream()
                .map(option -> new SkillEvolutionAuthoredCandidateOption(
                        SkillEvolutionAuthoredCandidate.from(evalCaseFactory.enrich(
                                option.candidate().payload(), consolidationSamples)),
                        option.audit()))
                .toList();
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

        SkillEvolutionSimilarityMatch similar = similarityPort.bestMatch(
                request.projectId(), legacyAuthored.payload());
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
                tournamentRolloutCoordinator.select(
                        request, evaluatedOptions, legacySelection, similar);
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
        return candidateCoordinator.create(
                request,
                opportunityType,
                signal,
                hint,
                experience,
                selectedHints,
                authored,
                effectiveSimilar);
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
}
