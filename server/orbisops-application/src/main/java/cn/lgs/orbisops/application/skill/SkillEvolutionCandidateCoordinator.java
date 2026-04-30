package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates, promotes and releases one authored Skill candidate after policy admission. */
public final class SkillEvolutionCandidateCoordinator {

    private final SkillPatchCandidateApplicationService candidateService;
    private final SkillExperienceApplicationService experienceService;
    private final SkillEvolutionSignalApplicationService signalService;
    private final SkillReleaseApplicationService releaseService;
    private final SkillEvolutionPipelineAuditPort auditPort;
    private final SkillEvolutionPipelineMapper mapper;

    public SkillEvolutionCandidateCoordinator(
            SkillPatchCandidateApplicationService candidateService,
            SkillExperienceApplicationService experienceService,
            SkillEvolutionSignalApplicationService signalService,
            SkillReleaseApplicationService releaseService,
            SkillEvolutionPipelineAuditPort auditPort,
            SkillEvolutionPipelineMapper mapper) {
        if (candidateService == null
                || experienceService == null
                || signalService == null
                || releaseService == null
                || auditPort == null
                || mapper == null) {
            throw new IllegalArgumentException(
                    "SKILL_EVOLUTION_CANDIDATE_DEPENDENCY_REQUIRED");
        }
        this.candidateService = candidateService;
        this.experienceService = experienceService;
        this.signalService = signalService;
        this.releaseService = releaseService;
        this.auditPort = auditPort;
        this.mapper = mapper;
    }

    SkillEvolutionPipelineDecision create(
            SkillEvolutionPipelineRequest request,
            String opportunityType,
            SkillEvolutionSignalSnapshot signal,
            SkillEvolutionHintSnapshot hint,
            SkillExperienceRecordResult experience,
            List<SkillEvolutionSelectedHint> selectedHints,
            SkillEvolutionAuthoredCandidate authored,
            SkillEvolutionSimilarityMatch similar) {
        if(cn.lgs.orbisops.domain.skill.model.SkillAtomicPublicationPlan.supports(authored.patchType()))
            similar=SkillEvolutionSimilarityMatch.none();
        Map<String, Object> candidateRequest = new LinkedHashMap<>(authored.payload());
        candidateRequest.put("sourceRunId", request.runId());
        candidateRequest.put("sourceType", opportunityType);
        candidateRequest.put("projectId", request.projectId());
        candidateRequest.put("agentId", request.agentId());
        candidateRequest.put("scope", "PROJECT");
        candidateRequest.put("targetSkillId", similar.skillId());
        candidateRequest.put("baseSkillVersion", similar.version());
        candidateRequest.put("baseSkillHash", similar.skillHash());
        candidateRequest.put(
                "contextBundleHash",
                request.summary().contextBundleHash());
        candidateRequest.put(
                "evidenceRefs",
                mapper.evidenceViews(request.summary().evidenceReferences()));
        Map<String, Object> candidate = candidateService.createEvolution(candidateRequest,request.jobClaim(),request.summary().sourceHash());
        String candidateId = mapper.text(candidate.get("candidate_id"));
        SkillReleaseStartOutcome releaseOutcome = releaseService.startOutcome(candidateId);
        experienceService.markPromoted(
                request.projectId(),
                request.agentId(),
                experience.observation().clusterKey(),
                candidateId);
        signalService.markHintsConsumed(
                selectedHints.stream()
                        .map(item -> item.hint().hintId())
                        .toList(),
                candidateId);
        Map<String, Object> release = releaseOutcome.view();
        auditPort.recordCandidateCreated(
                request.projectId(),
                request.agentId(),
                candidateId,
                releaseOutcome.statusCode(),
                Map.of(
                        "signal", mapper.signalView(signal),
                        "hint", mapper.hintView(hint),
                        "release", release,
                        "matchedSkillId", similar.skillId(),
                        "similarity", similar.similarity()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signal", mapper.signalView(signal));
        result.put("hint", mapper.hintView(hint));
        result.put("experience", mapper.experienceView(experience));
        result.put("candidateId", candidateId);
        result.put("targetSkillId", similar.skillId());
        result.put("patchType", authored.patchType());
        result.put("changes", authored.changes());
        result.put("artifacts", authored.artifacts());
        result.put("evalCases", authored.evalCases());
        result.put("authoringReason", authored.reason());
        result.put("authoringSource", authored.source());
        result.put("similarity", similar.similarity());
        result.put("release", release);
        result.put("status", releaseOutcome.statusCode());
        return mapper.decision(result);
    }
}
