package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsMcpUnifiedToolExecutorTest {

    @Test
    void nativeDispatchKeepsRetryIdentityAndSeparatesIndependentCalls() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        OpsMcpUnifiedToolExecutor executor = new OpsMcpUnifiedToolExecutor(() -> service);
        var keys = new java.util.ArrayList<String>();
        when(service.executeMcp(any(OpsMcpServerConfig.class), anyString(), anyString(), anyString()))
                .thenAnswer(call -> { keys.add(call.getArgument(3)); return Map.of("status", "SUCCEEDED"); });
        String input = "{\"toolName\":\"updateTestFixture\",\"arguments\":{\"value\":1}}";
        for (String id : List.of("call-a", "call-a", "call-b")) {
            var context = new org.springframework.ai.chat.model.ToolContext(Map.of(
                    OpsMcpDisclosureInterceptor.NATIVE_INVOCATION,
                    new OpsMcpDisclosureInterceptor.NativeInvocation("project_mcp_ops", input, id)));
            executor.executeDispatcher(config("PREPARE", "PREPARE_CHANGE", "test"), input, context,
                    List.of(Map.of("toolName", "updateTestFixture", "readOnly", false)), "project_mcp_ops");
        }
        assertEquals(keys.get(0), keys.get(1));
        org.junit.jupiter.api.Assertions.assertNotEquals(keys.get(0), keys.get(2));
    }

    @Test
    void prepareChangeDirectMutationReturnsProposalWithoutToolExecution() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        OpsMcpUnifiedToolExecutor executor = new OpsMcpUnifiedToolExecutor(() -> service);
        OpsMcpServerConfig config = config("PREPARE", "PREPARE_CHANGE");

        Map<String, Object> result = executor.executeDirect(
                config,
                "restartService",
                "{\"serviceId\":\"demo-project\"}",
                null,
                false);

        assertEquals("REQUIRES_CHANGE_PACKAGE", result.get("status"));
        assertEquals(false, result.get("remoteCallExecuted"));
        Map<?, ?> proposed = (Map<?, ?>) result.get("proposedAction");
        assertEquals("restartService", proposed.get("toolName"));
        assertEquals("PROPOSABLE_ONLY", proposed.get("exposure"));
        assertEquals("demo-project", ((Map<?, ?>) proposed.get("arguments")).get("serviceId"));
        verifyNoInteractions(service);
    }

    @Test
    void prepareChangeNonProductionMutationStillExecutesThroughToolExecution() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        OpsMcpUnifiedToolExecutor executor = new OpsMcpUnifiedToolExecutor(() -> service);
        OpsMcpServerConfig config = config("PREPARE", "PREPARE_CHANGE", "test");
        when(service.executeMcp(any(OpsMcpServerConfig.class), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("status", "SUCCEEDED"));

        Map<String, Object> result = executor.executeDirect(
                config,
                "updateTestFixture",
                "{\"value\":1}",
                null,
                false);

        assertEquals("SUCCEEDED", result.get("status"));
        verify(service).executeMcp(any(OpsMcpServerConfig.class), anyString(), anyString(), anyString());
    }

    @Test
    void prepareChangeProgressiveMutationReturnsProposalWithoutToolExecution() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        OpsMcpUnifiedToolExecutor executor = new OpsMcpUnifiedToolExecutor(() -> service);
        OpsMcpServerConfig config = config("PREPARE", "PREPARE_CHANGE");
        List<Map<String, Object>> runtimeTools = List.of(Map.of(
                "toolName", "restartService",
                "readOnly", false));

        Map<String, Object> result = executor.executeDispatcher(
                config,
                "{\"toolName\":\"restartService\",\"arguments\":{\"serviceId\":\"demo-project\"}}",
                null,
                runtimeTools,
                "project_mcp_ops");

        assertEquals("REQUIRES_CHANGE_PACKAGE", result.get("status"));
        assertFalse(Boolean.TRUE.equals(result.get("remoteCallExecuted")));
        assertTrue(result.containsKey("proposedAction"));
        verifyNoInteractions(service);
    }

    @Test
    void landingUsesFrozenExecutionBindingForMutationAndReceiptWithoutDoubleProjectingReceipt() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        OpsMcpUnifiedToolExecutor executor = new OpsMcpUnifiedToolExecutor(() -> service);
        OpsMcpServerConfig config = config("LANDING", "PROD_FULL");
        config.setLandingOperationBindings(List.of(new LandingOperationExecutionBinding(
                "operation-1", "execution-1", "mcp.ops-mcp", "restart_service",
                "service://demo")));
        when(service.executeLandingMcp(
                any(OpsMcpServerConfig.class), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyString()))
                .thenReturn(Map.of("status", "SUCCEEDED"));

        executor.executeDirect(
                config,
                "restart_service",
                "{\"service\":\"demo\",\"expectedVersion\":1}",
                null,
                false);
        executor.executeDirect(
                config,
                "get_operation_receipt",
                "{}",
                null,
                true);

        verify(service).executeLandingMcp(
                any(OpsMcpServerConfig.class), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq("execution-1"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq("operation-1"));
        verify(service).executeLandingMcp(
                any(OpsMcpServerConfig.class), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq("execution-1"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(""));
    }

    private OpsMcpServerConfig config(String stage, String authority) {
        return config(stage, authority, "production");
    }

    private OpsMcpServerConfig config(String stage, String authority, String environment) {
        return OpsMcpServerConfig.builder()
                .projectId("project-1")
                .runId("run-1")
                .nodeId("node-1")
                .mcpId("ops-mcp")
                .toolCallStage(stage)
                .runtimeAuthority(authority)
                .toolCapabilities(Map.of("resourceEnvironment", environment))
                .build();
    }
}
