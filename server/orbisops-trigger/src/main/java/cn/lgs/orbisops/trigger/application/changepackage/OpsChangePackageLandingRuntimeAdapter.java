package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRuntimePort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRuntimeResult;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.runtime.OpsApprovedLandingAgentRunCoordinator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed Chat action adapter: authorize one new LANDING Agent run, never execute production directly. */
@Component
public class OpsChangePackageLandingRuntimeAdapter implements ChangePackageLandingRuntimePort {

    private final OpsApprovedLandingAgentRunCoordinator landingAgentRunCoordinator;
    private final OpsLandingAgentRunAuthorizationService authorizationService =
            new OpsLandingAgentRunAuthorizationService();

    public OpsChangePackageLandingRuntimeAdapter(
            ObjectProvider<OpsApprovedLandingAgentRunCoordinator> coordinatorProvider) {
        this.landingAgentRunCoordinator = coordinatorProvider.getIfAvailable();
    }

    @Override
    public Map<String, Object> verifyCompletedOperations(ChangePackageCurrent current,
            ChangePackageVersion approvedVersion, ChangePackageLandingPlan plan, String landingRunId,
            String actor, java.util.List<cn.lgs.orbisops.application.changepackage.LandingOperationFact> facts) {
        if (landingAgentRunCoordinator == null) throw new IllegalStateException("LANDING_INDEPENDENT_VERIFIER_UNAVAILABLE");
        if (!current.packageId().equals(plan.packageId()) || !current.projectId().equals(plan.projectId())
                || current.pointer().approvedVersion() != approvedVersion.version()
                || !current.pointer().approvedPackageHash().equals(approvedVersion.packageHash())
                || plan.approvedVersion() != approvedVersion.version()
                || !plan.approvedPackageHash().equals(approvedVersion.packageHash())) {
            throw new SecurityException("LANDING_RECOVERY_APPROVED_POINTER_DRIFT");
        }
        return landingAgentRunCoordinator.verifyCompletedOperations(landingRunId, plan, actor, facts);
    }

    @Override
    public ChangePackageLandingRuntimeResult execute(ChangePackageCurrent current,
                                                     ChangePackageVersion approvedVersion,
                                                     ChangePackageLandingPlan plan,
                                                     ChangePackageLandingRequest request,
                                                     String landingRunId,
                                                     String actor) {
        Map<String, Object> payload = landingAgentRunCoordinator == null
                ? unavailable(current)
                : landingAgentRunCoordinator.execute(authorizationService.authorize(
                        current,
                        approvedVersion,
                        plan,
                        request,
                        landingRunId,
                        actor));
        return result(payload);
    }

    private ChangePackageLandingRuntimeResult result(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        ChangePackageStatus status = ChangePackageStatus.require(
                text(safe.get("status"), ChangePackageStatus.LANDING_FAILED.name()));
        return new ChangePackageLandingRuntimeResult(
                status,
                text(safe.get("eventType"), defaultEventType(status)),
                text(safe.get("reasonCode"), ""),
                text(safe.get("summary"), "Landing Agent Run 已完成一次状态评估"),
                Boolean.TRUE.equals(safe.get("executedProductionAction")),
                safe);
    }

    private String defaultEventType(ChangePackageStatus status) {
        return switch (status) {
            case LANDED -> "LANDING_SUCCEEDED";
            case LANDING_FAILED -> "LANDING_FAILED";
            case NEEDS_REPLAN -> "LANDING_NEEDS_REPLAN";
            default -> "LANDING_EVENT";
        };
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private Map<String, Object> unavailable(ChangePackageCurrent current) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packageId", current.packageId());
        result.put("landingRuntime", "UNIFIED_AGENT_RUNTIME_UNAVAILABLE");
        result.put("landingGraphEditable", false);
        result.put("executedProductionAction", false);
        if (current.packageType() == ChangePackageType.NO_ACTION_REQUIRED) {
            result.put("status", ChangePackageStatus.LANDED.name());
            result.put("eventType", "LANDING_NO_ACTION");
            result.put("reasonCode", "NO_ACTION_REQUIRED");
            result.put("summary", "该 ChangePackage 无需生产动作，Landing Agent Runtime 未初始化时仅允许关闭。");
            return result;
        }
        result.put("status", ChangePackageStatus.LANDING_FAILED.name());
        result.put("eventType", "LANDING_FAILED");
        result.put("reasonCode", "LANDING_AGENT_RUNTIME_UNAVAILABLE");
        result.put("summary", "UnifiedAgentRuntime 未初始化，禁止直接调用生产控制层；已审批方案仍有效，可在运行时恢复后重试。");
        return result;
    }
}
