package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Fully identified observation ready for durable Episode/Observation persistence. */
public record SkillExperienceObservation(
        String episodeId,
        String observationId,
        String clusterKey,
        String projectId,
        String agentId,
        String runId,
        String sessionId,
        String observationType,
        SkillExperienceTaskTemplate taskTemplate,
        String taskTemplateHash,
        List<String> abstractTrajectory,
        String trajectoryHash,
        String outcome,
        List<SkillExperienceEvidenceReference> evidenceReferences,
        String finalSummary,
        int eventCount,
        double qualityScore,
        VerifiedTaskOutcome verifiedTaskOutcome) {

    public SkillExperienceObservation(String episodeId, String observationId, String clusterKey, String projectId,
            String agentId, String runId, String sessionId, String observationType, SkillExperienceTaskTemplate taskTemplate,
            String taskTemplateHash, List<String> abstractTrajectory, String trajectoryHash, String outcome,
            List<SkillExperienceEvidenceReference> evidenceReferences, String finalSummary, int eventCount, double qualityScore) {
        this(episodeId, observationId, clusterKey, projectId, agentId, runId, sessionId, observationType, taskTemplate,
                taskTemplateHash, abstractTrajectory, trajectoryHash, outcome, evidenceReferences, finalSummary, eventCount,
                qualityScore, VerifiedTaskOutcome.unknown());
    }

    public SkillExperienceObservation {
        verifiedTaskOutcome = verifiedTaskOutcome == null ? VerifiedTaskOutcome.unknown() : verifiedTaskOutcome;
        abstractTrajectory = abstractTrajectory == null
                ? List.of()
                : List.copyOf(abstractTrajectory);
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : List.copyOf(evidenceReferences);
    }
}
