package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProgressiveMcpCallbackAdapterTest {

    @Test
    void repeatedStructuredObjectsMustSerializeAsCompleteJsonNotFastjsonReferences() throws Exception {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        var config = OpsMcpServerConfig.builder().toolId("read-fixture").build();
        Map<String, Object> content = Map.of("count", 3);
        Map<String, Object> envelope = Map.of("structuredContent", content, "normalizedContent", content, "isError", false);
        Map<String, Object> result = Map.of("mcpEnvelope", envelope, "normalizedContent", content, "providerResult", content);
        String input = "{\"toolName\":\"probe\",\"arguments\":{}}";
        when(service.executeMcp(config, input, "ops-agent", true)).thenReturn(result);
        var callback = new OpsProgressiveMcpCallbackAdapter(() -> service).dispatcherCallback(config,
                List.of(Map.of("toolName", "probe", "readOnly", true, "disclosureTier", "CORE")));
        String serialized = callback.call(input);
        var parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(serialized);
        for (String path : List.of("/mcpEnvelope/structuredContent/count", "/mcpEnvelope/normalizedContent/count",
                "/normalizedContent/count", "/providerResult/count")) assertEquals(3, parsed.at(path).asInt(), path);
        assertFalse(serialized.contains("\"$ref\""));
    }

    @Test
    void dispatcherMustDelegateToUnifiedToolExecutionFacade() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("demo-project-mcp")
                .toolId("demo-project-tool")
                .description("Demo project operations")
                .build();
        String input = "{\"toolName\":\"query_orders\",\"arguments\":{}}";
        when(executionService.executeMcp(config, input, "ops-agent", true))
                .thenReturn(Map.of("status", "SUCCEEDED", "remoteCallExecuted", true));
        OpsProgressiveMcpCallbackAdapter adapter =
                new OpsProgressiveMcpCallbackAdapter(() -> executionService);

        ToolCallback callback = adapter.dispatcherCallback(config, List.of(Map.of(
                "toolName", "query_orders",
                "readOnly", true,
                "disclosureTier", "CORE")));
        Map<String, Object> result = JSON.parseObject(callback.call(input), Map.class);

        assertEquals("project_mcp_demo_project_tool", callback.getToolDefinition().name());
        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("remoteCallExecuted"));
        verify(executionService).executeMcp(config, input, "ops-agent", true);
    }

    @Test
    void landingWriteDispatcherUsesApprovedLandingScopeAndStableIdempotencyKey() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("demo-project-mcp")
                .projectId("project-1")
                .runId("landing-run-1")
                .nodeId("landing-react")
                .toolId("demo-project-tool")
                .landingApproved(true)
                .toolCallStage("LANDING")
                .build();
        String input = "{\"toolName\":\"update_config\",\"arguments\":{\"value\":2}}";
        when(executionService.executeLandingMcp(
                org.mockito.ArgumentMatchers.eq(config),
                org.mockito.ArgumentMatchers.eq(input),
                org.mockito.ArgumentMatchers.eq("ops-agent"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        OpsProgressiveMcpCallbackAdapter adapter =
                new OpsProgressiveMcpCallbackAdapter(() -> executionService);
        ToolCallback callback = adapter.dispatcherCallback(config, List.of(Map.of(
                "toolName", "update_config",
                "readOnly", false,
                "disclosureTier", "CORE")));

        callback.call(input);
        callback.call(input);

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(executionService, org.mockito.Mockito.times(2)).executeLandingMcp(
                org.mockito.ArgumentMatchers.eq(config),
                org.mockito.ArgumentMatchers.eq(input),
                org.mockito.ArgumentMatchers.eq("ops-agent"),
                keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertTrue(keys.getValue().startsWith("mcp:"));
        assertFalse(keys.getValue().isBlank());
        verify(executionService, org.mockito.Mockito.never()).executeMcp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void landingWriteIdempotencySurvivesNewRunForSameApprovedPackage() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        OpsMcpServerConfig first = OpsMcpServerConfig.builder()
                .name("demo-project-mcp")
                .projectId("project-1")
                .runId("landing-run-1")
                .nodeId("landing-react")
                .toolId("demo-project-tool")
                .toolCallStage("LANDING")
                .changePackageId("cp-1")
                .approvedPackageHash("hash-1")
                .build();
        OpsMcpServerConfig retry = OpsMcpServerConfig.builder()
                .name("demo-project-mcp")
                .projectId("project-1")
                .runId("landing-run-2")
                .nodeId("landing-react")
                .toolId("demo-project-tool")
                .toolCallStage("LANDING")
                .changePackageId("cp-1")
                .approvedPackageHash("hash-1")
                .build();
        String input = "{\"toolName\":\"update_config\",\"arguments\":{\"value\":2}}";
        when(executionService.executeLandingMcp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(input),
                org.mockito.ArgumentMatchers.eq("ops-agent"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        OpsProgressiveMcpCallbackAdapter adapter =
                new OpsProgressiveMcpCallbackAdapter(() -> executionService);
        List<Map<String, Object>> tools = List.of(Map.of(
                "toolName", "update_config",
                "readOnly", false));

        adapter.dispatcherCallback(first, tools).call(input);
        adapter.dispatcherCallback(retry, tools).call(input);

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(executionService, org.mockito.Mockito.times(2)).executeLandingMcp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(input),
                org.mockito.ArgumentMatchers.eq("ops-agent"),
                keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }

    @Test
    void directDiscoveredCallbackUsesSchemaButNeverInvokesRemoteDelegateDirectly() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("legacy-mcp")
                .projectId("project-1")
                .runId("run-1")
                .nodeId("react-node")
                .toolId("legacy-tool")
                .toolCallStage("PREPARE")
                .build();
        ToolCallback discovered = mock(ToolCallback.class);
        when(discovered.getToolDefinition()).thenReturn(
                org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("update_config")
                        .description("update config")
                        .inputSchema("{\"type\":\"object\"}")
                        .build());
        when(discovered.getToolMetadata()).thenReturn(
                org.springframework.ai.tool.metadata.ToolMetadata.builder().build());
        when(executionService.executeMcp(
                org.mockito.ArgumentMatchers.eq(config),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("ops-agent"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        OpsProgressiveMcpCallbackAdapter adapter =
                new OpsProgressiveMcpCallbackAdapter(() -> executionService);

        Map<String, Object> result = JSON.parseObject(
                adapter.directCallback(config, discovered, false)
                        .call("{\"value\":2}"),
                Map.class);

        assertEquals("SUCCEEDED", result.get("status"));
        verify(discovered, org.mockito.Mockito.never()).call(org.mockito.ArgumentMatchers.anyString());
        verify(discovered, org.mockito.Mockito.never()).call(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(executionService).executeMcp(
                org.mockito.ArgumentMatchers.eq(config),
                org.mockito.ArgumentMatchers.contains("update_config"),
                org.mockito.ArgumentMatchers.eq("ops-agent"),
                org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("mcp:")));
    }

    @Test
    void blockedExecutionMustReturnCompatibilityEnvelopeWithoutRemoteCall() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("demo-project-mcp")
                .toolId("demo-project-tool")
                .build();
        String input = "{\"toolName\":\"query_orders\",\"arguments\":{}}";
        when(executionService.executeMcp(config, input, "ops-agent", true))
                .thenThrow(new OpsToolExecutionService.ToolBlockedException(
                        "MCP_POLICY_MISSING：tool policy missing",
                        Map.of("auditId", "audit-1")));
        OpsProgressiveMcpCallbackAdapter adapter =
                new OpsProgressiveMcpCallbackAdapter(() -> executionService);

        Map<String, Object> result = JSON.parseObject(
                adapter.dispatcherCallback(config, List.of(Map.of(
                        "toolName", "query_orders",
                        "readOnly", true))).call(input),
                Map.class);

        assertEquals("BLOCKED", result.get("status"));
        assertEquals(Boolean.FALSE, result.get("allowed"));
        assertEquals(Boolean.FALSE, result.get("remoteCallExecuted"));
        assertEquals("MCP_POLICY_MISSING", result.get("reasonCode"));
        assertEquals("audit-1", result.get("auditId"));
        assertTrue(String.valueOf(result.get("agentObservation")).contains("远端工具未执行"));
    }

    @Test
    void missingUnifiedExecutionServiceMustFailClosedForAllProgressiveCallbacks() {
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("demo-project-mcp")
                .toolId("demo-project-tool")
                .build();
        OpsProgressiveMcpCallbackAdapter adapter = new OpsProgressiveMcpCallbackAdapter(() -> null);

        assertThrows(SecurityException.class,
                () -> adapter.dispatcherCallback(config, List.of()).call("{}"));
        assertThrows(SecurityException.class,
                () -> adapter.catalogCallback(config).call("{}"));
        assertThrows(SecurityException.class,
                () -> adapter.enableCallback(config).call("{\"toolName\":\"query_orders\"}"));
        assertFalse(adapter.catalogCallback(config).getToolDefinition().name().isBlank());
    }
}
