package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.IdempotencyCapability;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolsetRouterTest {

    @Test
    void normalSessionCanUseReadOnlyAndRepairToolsButTargetWriteRequiresChangePackage() {
        OpsToolsetRegistry registry = new OpsToolsetRegistry();
        OpsToolsetRouter router = new OpsToolsetRouter();
        List<OpsToolsetDefinition> builtIns = registry.listBuiltInToolsets();

        OpsToolsetDefinition repo = find(builtIns, "code.repository");
        assertTrue((Boolean) router.decide(repo, repo.getTools().get(0), false, Map.of()).get("allowed"));

        OpsToolsetDefinition repair = find(builtIns, "code.repair");
        assertTrue((Boolean) router.decide(repair, repair.getTools().get(0), false, Map.of()).get("allowed"));

        OpsToolsetDefinition validation = OpsToolsetDefinition.builder()
                .toolsetId("validation.sandbox")
                .name("sandbox validation")
                .adapterType("LOCAL_VALIDATION")
                .enabled(true)
                .tools(List.of(OpsToolDefinition.builder()
                        .toolName("sandbox_validate")
                        .adapterType("LOCAL_VALIDATION")
                        .readOnly(false)
                        .writesRepairWorkspace(false)
                        .writesTargetResource(false)
                        .requiresChangePackage(true)
                        .requiresApproval(true)
                        .riskLevel("HIGH")
                        .enabled(true)
                        .build()))
                .build();
        assertTrue((Boolean) router.decide(validation, validation.getTools().get(0),
                OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of()).get("allowed"));

        OpsToolsetDefinition targetWrite = find(builtIns, "config.nacos.publish");
        Map<String, Object> blocked = router.decide(targetWrite, targetWrite.getTools().get(0), false, Map.of());
        assertFalse((Boolean) blocked.get("allowed"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", blocked.get("reasonCode"));

        OpsToolsetDefinition threshold = find(builtIns, UpdateAlertThresholdPolicy.TOOLSET_ID);
        OpsToolDefinition thresholdTool = threshold.getTools().get(0);
        Map<String, Object> thresholdBlocked = router.decide(
                threshold,
                thresholdTool,
                OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of());
        assertFalse((Boolean) thresholdBlocked.get("allowed"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE",
                thresholdBlocked.get("reasonCode"));
        assertEquals(IdempotencyCapability.SERVER_RECEIPT,
                thresholdTool.getGovernance().idempotencyCapability());

        Map<String, Object> allowed = router.decide(targetWrite, targetWrite.getTools().get(0), true, Map.of(
                "landingApproved", true,
                "changePackageId", "cp-1",
                "operationId", "op-1",
                "approvedPackageVersion", 1,
                "approvedPackageHash", "hash",
                "internalCaller", OpsToolsetRouter.LANDING_INTERNAL_CALLER,
                "landingRuntimeToken", OpsToolsetRouter.LANDING_RUNTIME_TOKEN));
        assertTrue((Boolean) allowed.get("allowed"));

        Map<String, Object> thresholdAllowed = router.decide(
                threshold,
                thresholdTool,
                OpsToolExecutionScope.APPROVED_LANDING,
                Map.of(
                        "landingApproved", true,
                        "changePackageId", "cp-1",
                        "operationId", "op-threshold-1",
                        "approvedPackageVersion", 1,
                        "approvedPackageHash", "hash",
                        "internalCaller", OpsToolsetRouter.LANDING_INTERNAL_CALLER,
                        "landingRuntimeToken", OpsToolsetRouter.LANDING_RUNTIME_TOKEN));
        assertTrue((Boolean) thresholdAllowed.get("allowed"));

        Map<String, Object> forged = router.decide(targetWrite, targetWrite.getTools().get(0), true, Map.of(
                "landingApproved", true,
                "changePackageId", "cp-1",
                "operationId", "op-1",
                "approvedPackageVersion", 1,
                "approvedPackageHash", "hash"));
        assertFalse((Boolean) forged.get("allowed"));
        assertEquals("APPROVED_LANDING_INTERNAL_CALLER_REQUIRED", forged.get("reasonCode"));
    }

    private OpsToolsetDefinition find(List<OpsToolsetDefinition> list, String id) {
        return list.stream().filter(item -> id.equals(item.getToolsetId())).findFirst().orElseThrow();
    }
}
