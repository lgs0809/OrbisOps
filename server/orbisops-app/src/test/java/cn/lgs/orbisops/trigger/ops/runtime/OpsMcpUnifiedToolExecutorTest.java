package cn.lgs.orbisops.trigger.ops.runtime;

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
