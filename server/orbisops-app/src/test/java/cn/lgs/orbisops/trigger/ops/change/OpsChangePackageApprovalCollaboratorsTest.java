package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChangePackageApprovalCollaboratorsTest {

    @Test
    void snapshotReaderPrefersPreferredPlanBeforeLegacyMcpSteps() {
        Map<String, Object> preferred = Map.of("operationId", "preferred");
        Map<String, Object> legacy = Map.of("operationId", "legacy");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("riskLevel", "LOW");
        values.put("landingPlanJson", JSON.toJSONString(Map.of(
                "preferredPlan", Map.of("steps", List.of(preferred)))));
        values.put("mcpSteps", List.of(legacy));
        ChangePackageSnapshot snapshot = ChangePackageSnapshot.seal(values);
        ChangePackageVersion version = new ChangePackageVersion(
                1,
                "cp-1",
                1,
                snapshot.packageHash(),
                ChangePackageStatus.READY_FOR_REVIEW.name(),
                snapshot,
                "",
                "alice",
                null);

        OpsChangePackageApprovalSnapshotReader.ApprovalSnapshot result =
                new OpsChangePackageApprovalSnapshotReader().read(version);

        assertEquals("preferred", result.operations().get(0).get("operationId"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.operations().get(0).put("operationId", "changed"));
    }

    @Test
    void operationMapperPreservesApprovalCompatibilityAliases() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("operationId", "op-1");
        raw.put("toolsetId", "toolset-primary");
        raw.put("mcpId", "mcp-secondary");
        raw.put("remoteToolName", "remote_validate");
        raw.put("adapterType", "MCP");
        raw.put("arguments", Map.of());
        raw.put("resourceScope", "demo-project/config");
        raw.put("riskLevel", "HIGH");
        raw.put("targetEnvironment", "prod");
        raw.put("effectType", "MUTATE_TEMP_RESOURCE");
        raw.put("effectScope", "TEST");
        raw.put("mutability", "TEMP_MUTATING");
        raw.put("readOnly", 0);
        raw.put("writesTargetResource", 0);
        raw.put("requiresChangePackage", 1);
        raw.put("requiresApproval", "true");
        raw.put("arguments_hash", "arguments-hash");
        raw.put("precondition_hash", "precondition-hash");
        raw.put("post_check_hash", "post-check-hash");
        raw.put("rollback_hash", "rollback-hash");
        raw.put("operation_hash", "operation-hash");

        ChangePackageApprovalOperation mapped =
                new OpsChangePackageApprovalOperationMapper().map(raw);

        assertEquals("toolset-primary", mapped.mcpId());
        assertEquals("remote_validate", mapped.toolName());
        assertEquals("MUTATE_EPHEMERAL", mapped.effectType());
        assertFalse(mapped.writesTargetResource());
        assertTrue(mapped.requiresChangePackage());
        assertTrue(mapped.requiresApproval());
        assertEquals("post-check-hash", mapped.postCheckHash());
        assertTrue(mapped.present("readOnly"));
    }

    @Test
    void missingRiskPresenceIsNotHiddenByHighRiskFallback() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("operationId", "op-1");
        raw.put("mcpId", "mcp-1");
        raw.put("toolName", "validate");

        ChangePackageApprovalOperation mapped =
                new OpsChangePackageApprovalOperationMapper().map(raw);

        assertEquals("HIGH", mapped.riskLevel());
        assertFalse(mapped.present("riskLevel"));
    }
}
