package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.capability.CapabilityDependencyReadiness;
import cn.lgs.orbisops.application.capability.CapabilityReadinessEnvironment;
import cn.lgs.orbisops.application.capability.CapabilityReadinessEnvironmentPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessSnapshot;
import cn.lgs.orbisops.trigger.ops.change.OpsLandingOperationRecoveryService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolRuntimeReadiness;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Trigger adapter normalizing dependency probes and Landing runtime facts. */
public final class OpsCapabilityReadinessEnvironmentAdapter implements CapabilityReadinessEnvironmentPort {

    private final OpsToolResultStore toolResultStore;
    private final OpsTrustedProofService trustedProofService;
    private final OpsConfigAuditService auditService;
    private final ChangePackageReadinessPort changePackageReadiness;
    private final OpsLandingOperationRecoveryService landingRecoveryService;
    private final OpsToolRuntimeReadiness toolRuntimeReadiness;

    public OpsCapabilityReadinessEnvironmentAdapter(
            OpsToolResultStore toolResultStore,
            OpsTrustedProofService trustedProofService,
            OpsConfigAuditService auditService,
            ChangePackageReadinessPort changePackageReadiness,
            OpsLandingOperationRecoveryService landingRecoveryService) {
        this(
                toolResultStore,
                trustedProofService,
                auditService,
                changePackageReadiness,
                landingRecoveryService,
                OpsToolRuntimeReadiness.compatibility());
    }

    public OpsCapabilityReadinessEnvironmentAdapter(
            OpsToolResultStore toolResultStore,
            OpsTrustedProofService trustedProofService,
            OpsConfigAuditService auditService,
            ChangePackageReadinessPort changePackageReadiness,
            OpsLandingOperationRecoveryService landingRecoveryService,
            OpsToolRuntimeReadiness toolRuntimeReadiness) {
        this.toolResultStore = required(toolResultStore, "OPS_TOOL_RESULT_STORE_REQUIRED");
        this.trustedProofService = required(trustedProofService, "OPS_TRUSTED_PROOF_SERVICE_REQUIRED");
        this.auditService = required(auditService, "OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        this.changePackageReadiness = required(changePackageReadiness, "CHANGE_PACKAGE_READINESS_REQUIRED");
        this.landingRecoveryService = landingRecoveryService;
        this.toolRuntimeReadiness = required(
                toolRuntimeReadiness,
                "TOOL_RUNTIME_READINESS_REQUIRED");
    }

    @Override
    public CapabilityReadinessEnvironment inspect() {
        List<CapabilityDependencyReadiness> dependencies = new ArrayList<>();
        dependencies.add(probe("toolResultStore", toolResultStore::readiness));
        dependencies.add(probe("auditStore", auditService::readiness));
        dependencies.add(probe("trustedProofStore", trustedProofService::readiness));
        dependencies.add(changePackageProbe());
        CapabilityDependencyReadiness toolRuntime = probe("toolRuntimeProfile", toolRuntimeReadiness::readiness);
        dependencies.add(toolRuntime);
        return new CapabilityReadinessEnvironment(
                dependencies,
                changePackageReadiness.approvedLandingEnabled(),
                changePackageReadiness.operationJournalReady(),
                landingRecoveryService != null && landingRecoveryService.isEnabled(),
                toolRuntime.up());
    }

    private CapabilityDependencyReadiness changePackageProbe() {
        try {
            ChangePackageReadinessSnapshot snapshot = changePackageReadiness.readiness();
            return new CapabilityDependencyReadiness(
                    "changePackageStore",
                    snapshot.up(),
                    snapshot.reason(),
                    false,
                    false,
                    snapshot.details());
        } catch (RuntimeException error) {
            String reason = safeMessage(error);
            return new CapabilityDependencyReadiness(
                    "changePackageStore",
                    false,
                    reason,
                    false,
                    false,
                    Map.of("status", "DOWN", "reason", reason));
        }
    }

    private CapabilityDependencyReadiness probe(
            String name,
            Supplier<Map<String, Object>> supplier) {
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            Map<String, Object> supplied = supplier.get();
            if (supplied != null) {
                detail.putAll(supplied);
            }
            String status = String.valueOf(detail.getOrDefault("status", "DOWN"));
            boolean up = "UP".equalsIgnoreCase(status);
            detail.put("status", up ? "UP" : "DOWN");
            return new CapabilityDependencyReadiness(
                    name,
                    up,
                    string(detail.get("reason")),
                    Boolean.TRUE.equals(detail.get("usableForValidation")),
                    Boolean.TRUE.equals(detail.get("productionLandingEligible")),
                    detail);
        } catch (RuntimeException error) {
            String reason = safeMessage(error);
            detail.put("status", "DOWN");
            detail.put("reason", reason);
            return new CapabilityDependencyReadiness(
                    name, false, reason, false, false, detail);
        }
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private <T> T required(T value, String error) {
        if (value == null) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }
}
