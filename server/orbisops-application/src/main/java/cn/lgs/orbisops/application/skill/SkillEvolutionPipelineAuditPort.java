package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** Outbound audit boundary for Skill Evolution pipeline decisions. */
public interface SkillEvolutionPipelineAuditPort {

    void recordSkipped(
            String projectId,
            String agentId,
            String runId,
            String reasonCode,
            Map<String, Object> details);

    void recordCandidateCreated(
            String projectId,
            String agentId,
            String candidateId,
            String releaseStatus,
            Map<String, Object> details);

    default void recordCandidateSelectionCompared(
            String projectId,
            String agentId,
            String runId,
            String rolloutMode,
            Map<String, Object> details) {
        // Compatibility no-op. Production adapters should persist the comparison.
    }
}
