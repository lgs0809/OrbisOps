package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePackageLandingPlanFactoryTest {

    @Test
    void excludesPreApprovalDryRunFromExecutableLandingOperations() {
        ChangePackageSnapshot snapshot = snapshot();
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.APPROVED, 1, snapshot.packageHash(), 1, snapshot.packageHash());
        ChangePackageCurrent current = new ChangePackageCurrent(
                1L,
                pointer,
                "session-1",
                "",
                "demo-project",
                "agent-1",
                1,
                ChangePackageType.MCP_OPERATION_PACKAGE,
                ChangePackageCurrentState.fromSnapshot(snapshot.toMap()),
                snapshot,
                "",
                "alice",
                "bob",
                null,
                null,
                null);
        ChangePackageVersion version = new ChangePackageVersion(
                1L, "cp-1", 1, snapshot.packageHash(), ChangePackageStatus.READY_FOR_REVIEW.name(),
                snapshot, "initial", "alice", null);

        ChangePackageLandingPlan plan = new ChangePackageLandingPlanFactory().create(current, version);

        assertEquals(1, plan.operations().size());
        assertEquals("restart-service", plan.operations().get(0).operationId());
        assertEquals("restart_service", plan.operations().get(0).toolName());
        assertEquals("EXECUTE_EXTERNAL_ACTION", plan.operations().get(0).effectType());
    }

    private ChangePackageSnapshot snapshot() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("projectId", "demo-project");
        source.put("packageType", "MCP_OPERATION_PACKAGE");
        source.put("status", "READY_FOR_REVIEW");
        source.put("riskLevel", "HIGH");
        source.put("contextBundleId", "bundle-1");
        source.put("contextBundleHash", "bundle-hash");
        source.put("usedSkillVersionRefs", List.of());
        source.put("usedSkillRefsHash", "skill-refs-hash");
        source.put("toolsetBoundaryHash", "toolset-boundary-hash");
        source.put("runtimeBoundaryHash", "runtime-boundary-hash");
        source.put("approvalBoundary", Map.of("projectId", "demo-project"));
        source.put("mcpSteps", List.of(
                Map.ofEntries(
                        Map.entry("operationId", "validate-restart"),
                        Map.entry("mcpId", "order-service-control-mcp"),
                        Map.entry("toolName", "restart_service_dry_run"),
                        Map.entry("effectType", "DRY_RUN"),
                        Map.entry("effectScope", "VALIDATION_SANDBOX"),
                        Map.entry("mutability", "READ_ONLY"),
                        Map.entry("readOnly", true),
                        Map.entry("writesTargetResource", false),
                        Map.entry("riskLevel", "MEDIUM"),
                        Map.entry("arguments", Map.of("service", "order-service")),
                        Map.entry("preconditions", Map.of("schemaValid", true)),
                        Map.entry("postCheck", Map.of("status", "PASSED")),
                        Map.entry("rollbackPlan", Map.of("required", false))),
                Map.ofEntries(
                        Map.entry("operationId", "restart-service"),
                        Map.entry("mcpId", "order-service-control-mcp"),
                        Map.entry("toolName", "restart_service"),
                        Map.entry("effectType", "EXECUTE_EXTERNAL_ACTION"),
                        Map.entry("effectScope", "PRODUCTION"),
                        Map.entry("mutability", "WRITE"),
                        Map.entry("readOnly", false),
                        Map.entry("writesTargetResource", true),
                        Map.entry("riskLevel", "HIGH"),
                        Map.entry("arguments", Map.of("service", "order-service", "expectedVersion", 1)),
                        Map.entry("preconditions", Map.of("schemaValid", true)),
                        Map.entry("postCheck", Map.of("expectedValues", Map.of(
                                "serviceStatus", "RUNNING", "version", 2, "restartCount", 1))),
                        Map.entry("rollbackPlan", Map.of("required", true)),
                        Map.entry("rollbackPrecondition", Map.of("requiresHumanApproval", true)),
                        Map.entry("manualFallback", Map.of("required", true)))));
        return new ChangePackageSnapshotFactory().create("cp-1", 1, source, "alice");
    }
}
