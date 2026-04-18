package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsPreApprovalValidationExecutorsTest {

    @Test
    void repairExecutorFailsClosedWhenRequiredAdaptersAreUnavailable() {
        OpsPreApprovalRepairValidationExecutor executor =
                new OpsPreApprovalRepairValidationExecutor(
                        null,
                        null,
                        new OpsPreApprovalProofService(null));

        OpsPreApprovalValidationExecutionResult result =
                executor.validate(Map.of(), "alice");

        assertFalse(result.proofObtained());
        assertEquals(List.of("REPAIR_VALIDATION_SERVICE_UNAVAILABLE"), result.errors());
    }

    @Test
    void mcpExecutorReusesExistingTrustedProofWithoutRemoteExecution() {
        OpsTrustedProofService trustedProofService = mock(OpsTrustedProofService.class);
        when(trustedProofService.verifyTrustedProof(
                "project-1", "cp-1", 2, "hash-2", "HIGH",
                "MCP_DRY_RUN", ""))
                .thenReturn(true);
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        OpsPreApprovalMcpValidationExecutor executor =
                new OpsPreApprovalMcpValidationExecutor(
                        toolExecutionService,
                        mock(OpsProjectMcpRuntimeConfigService.class),
                        new OpsPreApprovalProofService(trustedProofService));

        OpsPreApprovalValidationExecutionResult result = executor.obtainProof(
                snapshot(),
                List.of(operation()),
                "alice");

        assertTrue(result.proofObtained());
        assertTrue(result.errors().isEmpty());
        assertEquals("MCP_DRY_RUN", result.proofs().get(0).get("proofType"));
        verify(toolExecutionService, never()).executeMcp(any(), anyString(), anyString());
    }

    @Test
    void mcpExecutorSerializesRemotePayloadAndRecordsProof() {
        OpsTrustedProofService trustedProofService = mock(OpsTrustedProofService.class);
        when(trustedProofService.recordTrustedProof(any(), eq("alice")))
                .thenReturn(Map.of(
                        "proofId", "proof-1",
                        "proofType", "MCP_DRY_RUN",
                        "metadata", Map.of("outputHash", "out-1")));
        OpsToolExecutionService toolExecutionService = mock(OpsToolExecutionService.class);
        when(toolExecutionService.executeMcp(any(), anyString(), eq("alice")))
                .thenReturn(Map.of("resultId", "result-1", "outputHash", "out-1"));
        OpsProjectMcpRuntimeConfigService runtimeConfigService =
                mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpServerConfig config = new OpsMcpServerConfig();
        when(runtimeConfigService.resolve("project-1", "mcp-1"))
                .thenReturn(Optional.of(config));
        OpsPreApprovalMcpValidationExecutor executor =
                new OpsPreApprovalMcpValidationExecutor(
                        toolExecutionService,
                        runtimeConfigService,
                        new OpsPreApprovalProofService(trustedProofService));

        OpsPreApprovalValidationExecutionResult result = executor.obtainProof(
                snapshot(),
                List.of(operation()),
                "alice");

        assertTrue(result.proofObtained());
        assertTrue(result.errors().isEmpty());
        assertEquals("proof-1", result.proofs().get(0).get("proofId"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(toolExecutionService).executeMcp(any(), payload.capture(), eq("alice"));
        assertTrue(payload.getValue().contains("remote_validate"));
        assertTrue(payload.getValue().contains("resourceId"));
        assertEquals("project-1", config.getProjectId());
        assertEquals("PREPARE", config.getToolCallStage());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> proofPayload = ArgumentCaptor.forClass(Map.class);
        verify(trustedProofService).recordTrustedProof(proofPayload.capture(), eq("alice"));
        assertEquals("HIGH", proofPayload.getValue().get("riskLevel"));
    }

    @Test
    void mcpExecutorPreservesUnavailableErrorsAndImmutableResult() {
        OpsPreApprovalMcpValidationExecutor executor =
                new OpsPreApprovalMcpValidationExecutor(
                        null,
                        null,
                        new OpsPreApprovalProofService(mock(OpsTrustedProofService.class)));

        OpsPreApprovalValidationExecutionResult result = executor.obtainProof(
                snapshot(),
                List.of(operation()),
                "alice");

        assertEquals(List.of(
                "MCP_VALIDATION_EXECUTOR_UNAVAILABLE",
                "MISSING_TRUSTED_VALIDATION_PROOF"), result.errors());
        assertThrows(UnsupportedOperationException.class,
                () -> result.errors().add("CHANGED"));
    }

    private Map<String, Object> snapshot() {
        return Map.of(
                "projectId", "project-1",
                "packageId", "cp-1",
                "version", 2,
                "packageHash", "hash-2",
                "riskLevel", "HIGH",
                "runId", "run-1");
    }

    private OpsPreApprovalOperationMapper.MappedOperation operation() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("operationId", "op-1");
        raw.put("toolName", "local_validate");
        raw.put("remoteToolName", "remote_validate");
        raw.put("adapterType", "MCP");
        raw.put("mcpId", "mcp-1");
        raw.put("arguments", Map.of("resourceId", "resource-1"));
        raw.put("resourceScope", "demo-project/config");
        raw.put("targetEnvironment", "test");
        raw.put("riskLevel", "MEDIUM");
        raw.put("effectType", "DRY_RUN");
        raw.put("effectScope", "TEST");
        raw.put("mutability", "READ_ONLY");
        raw.put("readOnly", true);
        raw.put("writesTargetResource", false);
        raw.put("requiresChangePackage", true);
        raw.put("requiresApproval", true);
        return new OpsPreApprovalOperationMapper().map(List.of(raw)).get(0);
    }
}
