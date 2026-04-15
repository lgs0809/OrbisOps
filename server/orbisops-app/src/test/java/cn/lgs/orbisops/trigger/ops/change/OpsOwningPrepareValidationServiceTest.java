package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsOwningPrepareValidationServiceTest {

    @Test
    void executesOnlySafeDryRunAndProjectsServerOwnedProof() {
        OpsToolExecutionService tools = mock(OpsToolExecutionService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder().mcpId("service-control").build();
        when(configs.resolve("demo-project", "service-control")).thenReturn(Optional.of(config));
        when(tools.executeMcp(eq(config), anyString(), eq("alice"), anyString())).thenReturn(Map.of(
                "resultId", "tool-result-dry-run",
                "outputHash", "a".repeat(64),
                "status", "PASSED",
                "projectId", "demo-project",
                "service", "order-service",
                "serviceStatus", "RUNNING",
                "expectedVersion", 7,
                "restartCount", 3,
                "writesTargetResource", false));
        OpsOwningPrepareValidationService service = new OpsOwningPrepareValidationService(tools, configs);

        Map<String, Object> enriched = service.enrich(request(List.of(
                operation("validate", "restart_service_dry_run", "DRY_RUN", "VALIDATION_SANDBOX", "READ_ONLY", false),
                operation("restart", "restart_service", "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "WRITE", true))), "alice");

        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) enriched.get("prepareExecution");
        @SuppressWarnings("unchecked")
        Map<String, Object> proof = (Map<String, Object>) execution.get("dryRunResult");
        assertEquals("SUCCEEDED", execution.get("status"));
        assertEquals(true, execution.get("verified"));
        assertEquals("PASSED", proof.get("status"));
        assertEquals("OWNING_PREPARE_TOOL_EXECUTION", proof.get("source"));
        assertEquals("tool-result-dry-run", proof.get("toolResultId"));
        assertEquals("a".repeat(64), proof.get("outputHash"));
        assertEquals(7, proof.get("observedExpectedVersion"));
        assertEquals(3, proof.get("observedRestartCount"));
        assertEquals("RUNNING", proof.get("observedServiceStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frozenSteps = (List<Map<String, Object>>) enriched.get("mcpSteps");
        @SuppressWarnings("unchecked")
        Map<String, Object> restartArguments = (Map<String, Object>) frozenSteps.get(1).get("arguments");
        assertEquals(7, restartArguments.get("expectedVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> postCheck = (Map<String, Object>) frozenSteps.get(1).get("postCheck");
        @SuppressWarnings("unchecked")
        Map<String, Object> expectedValues = (Map<String, Object>) postCheck.get("expectedValues");
        assertEquals("get_service_status", postCheck.get("sourceTool"));
        assertEquals("RUNNING", expectedValues.get("serviceStatus"));
        assertEquals(8, expectedValues.get("version"));
        assertEquals(4, expectedValues.get("restartCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> rollbackPlan = (Map<String, Object>) frozenSteps.get(1).get("rollbackPlan");
        @SuppressWarnings("unchecked")
        Map<String, Object> rollbackPrecondition = (Map<String, Object>) frozenSteps.get(1).get("rollbackPrecondition");
        @SuppressWarnings("unchecked")
        Map<String, Object> manualFallback = (Map<String, Object>) frozenSteps.get(1).get("manualFallback");
        assertEquals(true, rollbackPlan.get("required"));
        assertEquals(false, rollbackPlan.get("automaticMutation"));
        assertEquals(false, rollbackPrecondition.get("automaticRollback"));
        assertEquals(true, manualFallback.get("required"));
        assertEquals(false, manualFallback.get("automaticMutation"));
        assertTrue(String.valueOf(proof.get("idempotencyKey")).startsWith("change-package:prepare-validation:"));
        verify(tools, times(1)).executeMcp(eq(config), anyString(), eq("alice"), anyString());
        ArgumentCaptor<String> rawInput = ArgumentCaptor.forClass(String.class);
        verify(tools).executeMcp(eq(config), rawInput.capture(), eq("alice"), anyString());
        assertTrue(rawInput.getValue().contains("restart_service_dry_run"));
        assertFalse(rawInput.getValue().contains("\"toolName\":\"restart_service\""));
        assertEquals("PREPARE", config.getToolCallStage());
    }

    @Test
    void recordsFailedTrustedAttemptWhenPersistedPolicyBlocksSpoofedValidationTool() {
        OpsToolExecutionService tools = mock(OpsToolExecutionService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder().mcpId("service-control").build();
        when(configs.resolve("demo-project", "service-control")).thenReturn(Optional.of(config));
        when(tools.executeMcp(eq(config), anyString(), eq("alice"), anyString()))
                .thenThrow(new OpsToolExecutionService.ToolBlockedException(
                        "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", Map.of()));
        OpsOwningPrepareValidationService service = new OpsOwningPrepareValidationService(tools, configs);

        Map<String, Object> enriched = service.enrich(request(List.of(
                operation("spoofed", "restart_service", "DRY_RUN", "VALIDATION_SANDBOX", "READ_ONLY", false))), "alice");

        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) enriched.get("prepareExecution");
        @SuppressWarnings("unchecked")
        Map<String, Object> proof = (Map<String, Object>) execution.get("dryRunResult");
        assertEquals("FAILED", execution.get("status"));
        assertEquals(false, execution.get("verified"));
        assertEquals("FAILED", proof.get("status"));
        assertTrue(String.valueOf(proof.get("reason")).contains("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE"));
    }

    @Test
    void neverRunsProductionOnlyOperationDuringPrepare() {
        OpsToolExecutionService tools = mock(OpsToolExecutionService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        OpsOwningPrepareValidationService service = new OpsOwningPrepareValidationService(tools, configs);

        Map<String, Object> enriched = service.enrich(request(List.of(
                operation("restart", "restart_service", "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "WRITE", true))), "alice");

        assertFalse(enriched.containsKey("prepareExecution"));
        verify(tools, times(0)).executeMcp(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString());
    }

    private Map<String, Object> request(List<Map<String, Object>> operations) {
        return Map.of(
                "projectId", "demo-project",
                "runId", "run-1",
                "mcpSteps", operations);
    }

    private Map<String, Object> operation(String operationId,
                                          String toolName,
                                          String effectType,
                                          String effectScope,
                                          String mutability,
                                          boolean writesTargetResource) {
        return Map.ofEntries(
                Map.entry("operationId", operationId),
                Map.entry("mcpId", "service-control"),
                Map.entry("toolName", toolName),
                Map.entry("remoteToolName", toolName),
                Map.entry("adapterType", "MCP"),
                Map.entry("arguments", Map.of("service", "order-service")),
                Map.entry("resourceScope", "service:order-service"),
                Map.entry("targetEnvironment", writesTargetResource ? "PRODUCTION" : "VALIDATION"),
                Map.entry("riskLevel", writesTargetResource ? "HIGH" : "MEDIUM"),
                Map.entry("effectType", effectType),
                Map.entry("effectScope", effectScope),
                Map.entry("mutability", mutability),
                Map.entry("readOnly", !writesTargetResource),
                Map.entry("writesTargetResource", writesTargetResource),
                Map.entry("requiresChangePackage", true),
                Map.entry("requiresApproval", writesTargetResource));
    }
}
