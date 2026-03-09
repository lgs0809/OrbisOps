package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingSignalPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillEvolutionSignalService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class OpsChangePackageLandingSignalAdapter implements ChangePackageLandingSignalPort {

    private final OpsSkillEvolutionSignalService signalService;
    private final OpsSkillRuntimeUsageRecorder usageRecorder;
    private final OpsConfigAuditService auditService;

    public OpsChangePackageLandingSignalAdapter(
            ObjectProvider<OpsSkillEvolutionSignalService> signalServiceProvider,
            ObjectProvider<OpsSkillRuntimeUsageRecorder> usageRecorderProvider,
            ObjectProvider<OpsConfigAuditService> auditServiceProvider) {
        this.signalService = signalServiceProvider.getIfAvailable();
        this.usageRecorder = usageRecorderProvider.getIfAvailable();
        this.auditService = auditServiceProvider.getIfAvailable();
    }

    @Override
    public void recordOutcome(ChangePackageCurrent current,
                              ChangePackageVersion approvedVersion,
                              String landingRunId,
                              String status,
                              Map<String, Object> result) {
        Map<String, Object> snapshot = approvedVersion.snapshot().toMap();
        if (signalService != null) {
            String signalType = ChangePackageStatus.LANDED.name().equals(status)
                    ? "LANDING_SUCCESS_PATTERN"
                    : ChangePackageStatus.NEEDS_REPLAN.name().equals(status)
                    ? "REPEATED_GAP" : "";
            if (!signalType.isBlank()) {
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("packageId", current.packageId());
                    payload.put("approvedVersion", approvedVersion.version());
                    payload.put("approvedPackageHash", approvedVersion.packageHash());
                    payload.put("landingRunId", landingRunId);
                    payload.put("reasonCode", text(result == null ? null : result.get("reasonCode")));
                    signalService.record(signalType, current.projectId(), current.preparationAgentId(),
                            firstNonBlank(snapshot.get("runId"), snapshot.get("sourceRunId"), current.packageId()),
                            current.sessionId(), payload);
                } catch (RuntimeException error) {
                    log.warn("记录 Skill evolution signal 失败，不影响已完成的 ChangePackage 安全动作 packageId={} status={} reason={}",
                            current.packageId(), status, error.getMessage());
                }
            }
        }
        reconcile(snapshot, status);
    }

    private void reconcile(Map<String, Object> snapshot, String status) {
        if (usageRecorder == null || snapshot.isEmpty()) return;
        String projectId = text(snapshot.get("projectId"));
        String runId = firstNonBlank(snapshot.get("runId"), snapshot.get("sourceRunId"));
        if (projectId.isBlank() || runId.isBlank()) return;
        try {
            usageRecorder.reconcileRunOutcome(projectId, runId, Map.of(
                    "landingSucceeded", ChangePackageStatus.LANDED.name().equals(status),
                    "needsReplan", ChangePackageStatus.NEEDS_REPLAN.name().equals(status)));
        } catch (RuntimeException error) {
            log.warn("Skill 运行效果归因失败，主变更流程继续但记录审计 projectId={} runId={} reason={}",
                    projectId, runId, error.getMessage());
            if (auditService != null) {
                auditService.record(projectId, "skill-evolution", "outcome-reconcile-failed", runId, snapshot,
                        Map.of("reasonCode", "SKILL_OUTCOME_RECONCILIATION_FAILED",
                                "error", firstNonBlank(error.getMessage(), "unknown")));
            }
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
