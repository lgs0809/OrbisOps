package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalProofPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageAuditPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageSkillSignalPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageProofVerifier;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillEvolutionSignalService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class OpsChangePackageApprovalSupportAdapter implements ChangePackageApprovalProofPort,
        ChangePackageAuditPort,
        ChangePackageSkillSignalPort {

    private final ObjectProvider<OpsRepairWorkspaceService> repairWorkspaceServiceProvider;
    private final ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider;
    private final ObjectProvider<OpsConfigAuditService> auditServiceProvider;
    private final ObjectProvider<OpsSkillEvolutionSignalService> skillSignalServiceProvider;
    private final ObjectProvider<OpsSkillRuntimeUsageRecorder> skillRuntimeUsageRecorderProvider;

    public OpsChangePackageApprovalSupportAdapter(
            ObjectProvider<OpsRepairWorkspaceService> repairWorkspaceServiceProvider,
            ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider,
            ObjectProvider<OpsConfigAuditService> auditServiceProvider,
            ObjectProvider<OpsSkillEvolutionSignalService> skillSignalServiceProvider,
            ObjectProvider<OpsSkillRuntimeUsageRecorder> skillRuntimeUsageRecorderProvider) {
        this.repairWorkspaceServiceProvider = repairWorkspaceServiceProvider;
        this.trustedProofServiceProvider = trustedProofServiceProvider;
        this.auditServiceProvider = auditServiceProvider;
        this.skillSignalServiceProvider = skillSignalServiceProvider;
        this.skillRuntimeUsageRecorderProvider = skillRuntimeUsageRecorderProvider;
    }

    @Override
    public void verifyBeforeApprove(ChangePackageCurrent current, ChangePackageVersion version) {
        proofVerifier().verifyPackageBeforeApprove(current, version);
    }

    @Override
    public void record(String projectId,
                       String action,
                       String packageId,
                       ChangePackageCurrent before,
                       Map<String, Object> after) {
        OpsConfigAuditService auditService = auditServiceProvider.getIfAvailable();
        if (auditService != null) {
            // Creation has no previous aggregate by definition. Preserve that
            // semantic as a null "before" audit value instead of dereferencing
            // a non-existent ChangePackageCurrent.
            auditService.record(
                    projectId,
                    "change-package",
                    action,
                    packageId,
                    before == null ? null : currentView(before),
                    after);
        }
    }

    @Override
    public void recordAccepted(ChangePackageCurrent current, ChangePackageVersion approvedVersion, String actor) {
        OpsSkillEvolutionSignalService skillSignalService = skillSignalServiceProvider.getIfAvailable();
        if (skillSignalService == null) return;
        try {
            Map<String, Object> approvedSnapshot = approvedVersion.snapshot().toMap();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("packageId", current.packageId());
            payload.put("version", approvedVersion.version());
            payload.put("packageHash", approvedVersion.packageHash());
            payload.put("approvedBy", text(actor));
            skillSignalService.record(
                    "CHANGE_PACKAGE_ACCEPTED",
                    current.projectId(),
                    current.preparationAgentId(),
                    firstNonBlank(approvedSnapshot.get("runId"), approvedSnapshot.get("sourceRunId"), current.packageId()),
                    current.sessionId(),
                    payload);
        } catch (RuntimeException e) {
            log.warn("记录 Skill evolution signal 失败，不影响已完成的 ChangePackage 安全动作 packageId={} type={} reason={}",
                    current.packageId(), "CHANGE_PACKAGE_ACCEPTED", e.getMessage());
        }
    }

    @Override
    public void reconcileApprovedOutcome(ChangePackageVersion approvedVersion) {
        OpsSkillRuntimeUsageRecorder skillRuntimeUsageRecorder = skillRuntimeUsageRecorderProvider.getIfAvailable();
        if (skillRuntimeUsageRecorder == null) return;
        Map<String, Object> snapshot = approvedVersion.snapshot().toMap();
        String projectId = text(snapshot.get("projectId"));
        String runId = firstNonBlank(snapshot.get("runId"), snapshot.get("sourceRunId"));
        if (projectId.isBlank() || runId.isBlank()) return;
        try {
            skillRuntimeUsageRecorder.reconcileRunOutcome(projectId, runId,
                    Map.of("changePackageApproved", true));
        } catch (RuntimeException e) {
            log.warn("Skill 运行效果归因失败，主变更流程继续但记录审计 projectId={} runId={} reason={}",
                    projectId, runId, e.getMessage());
            OpsConfigAuditService auditService = auditServiceProvider.getIfAvailable();
            if (auditService != null) {
                auditService.record(projectId, "skill-evolution", "outcome-reconcile-failed", runId, snapshot,
                        Map.of("reasonCode", "SKILL_OUTCOME_RECONCILIATION_FAILED",
                                "error", firstNonBlank(e.getMessage(), "unknown")));
            }
        }
    }

    private OpsChangePackageProofVerifier proofVerifier() {
        return new OpsChangePackageProofVerifier(
                repairWorkspaceServiceProvider.getIfAvailable(),
                trustedProofServiceProvider.getIfAvailable());
    }

    private Map<String, Object> currentView(ChangePackageCurrent current) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", current.id());
        row.put("package_id", current.packageId());
        row.put("session_id", current.sessionId());
        row.put("incident_id", current.incidentId());
        row.put("project_id", current.projectId());
        row.put("preparation_agent_id", current.preparationAgentId());
        row.put("preparation_agent_version", current.preparationAgentVersion());
        row.put("package_type", current.packageType().name());
        row.put("status", current.status().name());
        row.put("version", current.version());
        row.put("package_hash", current.packageHash());
        row.put("approved_version", current.pointer().approvedVersion());
        row.put("approved_package_hash", current.pointer().approvedPackageHash());
        row.put("approved_snapshot_json",
                current.approvedSnapshot() == null ? null : current.approvedSnapshot().toMap());
        row.put("landing_run_id", current.landingRunId());
        row.put("create_by", current.createBy());
        row.put("approve_by", current.approveBy());
        row.put("create_time", current.createTime());
        row.put("update_time", current.updateTime());
        row.put("approved_at", current.approvedAt());
        current.state().values().forEach((field, value) -> row.put(columnName(field), value));
        return row;
    }

    private String columnName(ChangePackageCurrentField field) {
        return field.snapshotKey().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
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
