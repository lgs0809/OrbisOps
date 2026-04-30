package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** Audit boundary for Skill release state changes and reconciliation failures. */
public interface SkillReleaseAuditPort {

    void recordStateChanged(
            String projectId,
            String agentId,
            String releaseId,
            String status,
            String reasonCode,
            String skillId,
            Map<String, Object> metadata);

    void recordEvaluationFailed(
            String projectId,
            String releaseId,
            String error);
}
