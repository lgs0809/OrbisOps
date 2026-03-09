package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillReleaseAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger audit adapter for Skill release governance events. */
@Component
public class OpsSkillReleaseAuditAdapter implements SkillReleaseAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsSkillReleaseAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void recordStateChanged(
            String projectId,
            String agentId,
            String releaseId,
            String status,
            String reasonCode,
            String skillId,
            Map<String, Object> metadata) {
        auditService.recordRuntimeEvent(
                projectId,
                agentId,
                "",
                "skill-evolution",
                "release-" + text(status).toLowerCase(),
                releaseId,
                "LOW",
                status,
                metadata == null || metadata.isEmpty()
                        ? Map.of("reasonCode", text(reasonCode), "skillId", text(skillId))
                        : metadata);
    }

    @Override
    public void recordEvaluationFailed(
            String projectId,
            String releaseId,
            String error) {
        auditService.record(
                projectId,
                "skill-evolution",
                "release-evaluate-failed",
                releaseId,
                null,
                Map.of(
                        "error", text(error),
                        "reasonCode", "RECONCILIATION_REQUIRED"));
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
