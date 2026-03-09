package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeToolTraceDecoratorTest {

    @Test
    void emptyToolsMustRemainUndecorated() {
        OpsRuntimeResourceContext context = context(List.of(), new ArrayList<>());
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);

        assertTrue(context.getTools().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
    }

    @Test
    void decoratedToolMustNormalizeSchemaAndRecordSuccessfulLifecycle() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = ToolDefinition.builder()
                .name("Skill")
                .description("skill tool")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.call("{\"name\":\"logs\"}"))
                .thenReturn("skill output");
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);
        ToolCallback traced = context.getTools().get(0);
        String output = traced.call("{\"name\":\"logs\"}");

        assertEquals("skill output", output);
        assertTrue(traced.getToolDefinition().inputSchema().contains("properties"));
        assertEquals(null, traced.getToolMetadata());
        assertEquals(true, context.getMetadata().get("toolTraceEnabled"));
        assertEquals(1, context.getMetadata().get("toolCount"));
        assertEquals(List.of("TOOL_CALL_STARTED", "TOOL_CALL_FINISHED"),
                events.stream().map(OpsRuntimeEvent::getEventType).toList());
        assertEquals("skill", events.get(1).getPayload().get("toolKind"));
        assertEquals("Skill", events.get(1).getPayload().get("toolName"));
        assertEquals("skill output", events.get(1).getPayload().get("output"));
        assertEquals(12, events.get(1).getPayload().get("outputChars"));
    }

    @Test
    void successfulOpenApiDiscoveryMustProjectOnlyCurrentBusinessResource() {
        ToolCallback delegate = tool("project_mcp_demo_openapi_prod_readonly_mcp");
        String input = "{\"toolName\":\"openapi_list_operations\",\"arguments\":{}}";
        String output = "{\"resultId\":\"openapi-result-1\",\"outputHash\":\"hash-1\",\"providerResult\":{\"operations\":["
                + "{\"method\":\"POST\",\"path\":\"/api/demo-project/join\",\"summary\":\"示例参团\",\"operationId\":\"submitOrder\"},"
                + "{\"method\":\"POST\",\"path\":\"/api/demo-project/lock\",\"summary\":\"示例锁单\",\"operationId\":\"lockOrder\"}]}}";
        when(delegate.call(input)).thenReturn(output);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        context.getRequest().setQuery("示例参团最近是否异常");
        context.getRequest().getMetadata().put(
                OpsWorkSessionContextMetadataKeys.ORIGINAL_USER_QUERY,
                "示例参团最近是否异常");
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);
        context.getTools().get(0).call(input);

        assertEquals(List.of(
                        "TOOL_CALL_STARTED",
                        "TOOL_CALL_FINISHED",
                        OpsBusinessResourceIdentityProjector.RESOLVED_EVENT),
                events.stream().map(OpsRuntimeEvent::getEventType).toList());
        assertEquals(List.of("/api/demo-project/join"),
                OpsBusinessResourceIdentityProjector.resolvedPaths(context).stream().toList());
    }

    @Test
    void longManagedMcpOutputMustProjectExecutionProofOutsideAbbreviatedPreview() {
        ToolCallback delegate = tool("project_mcp_demo_openapi_prod_readonly_mcp");
        String input = "{\"toolName\":\"openapi_list_operations\",\"arguments\":{}}";
        String output = "{\"providerResult\":{\"blob\":\"" + "x".repeat(5000)
                + "\"},\"resultId\":\"tool-result-openapi-1\",\"providerId\":\"demo-project-openapi-prod-readonly-mcp\","
                + "\"remoteToolName\":\"openapi_list_operations\",\"allowed\":true,\"decision\":\"ALLOWED\",\"remoteCallExecuted\":true}";
        when(delegate.call(input)).thenReturn(output);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);
        context.getTools().get(0).call(input);

        OpsRuntimeEvent finished = events.get(1);
        assertEquals("TOOL_CALL_FINISHED", finished.getEventType());
        assertEquals(true, finished.getPayload().get("remoteCallExecuted"));
        assertEquals(true, finished.getPayload().get("allowed"));
        assertEquals("tool-result-openapi-1", finished.getPayload().get("resultId"));
        assertEquals("demo-project-openapi-prod-readonly-mcp", finished.getPayload().get("providerId"));
        assertEquals("openapi_list_operations", finished.getPayload().get("remoteToolName"));
        assertEquals("ALLOWED", finished.getPayload().get("decision"));
        assertTrue(String.valueOf(finished.getPayload().get("output")).length() < output.length());
    }

    @Test
    void proposedProductionActionMustNotBeRecordedAsSuccessfulToolExecution() {
        ToolCallback delegate = tool("restartService");
        when(delegate.call("{\"serviceId\":\"demo-project\"}"))
                .thenReturn("{\"status\":\"REQUIRES_CHANGE_PACKAGE\",\"allowed\":false,\"remoteCallExecuted\":false,\"proposedAction\":{\"toolName\":\"restartService\"}}");
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);
        context.getTools().get(0).call("{\"serviceId\":\"demo-project\"}");

        assertEquals(List.of("TOOL_CALL_STARTED", "TOOL_CALL_PROPOSED"),
                events.stream().map(OpsRuntimeEvent::getEventType).toList());
        assertEquals("PROPOSED", events.get(1).getStatus());
        assertTrue(events.stream().noneMatch(event -> "TOOL_CALL_FINISHED".equals(event.getEventType())));
    }

    @Test
    void policyBlockedEnvelopeMustNotBeRecordedAsSuccessfulToolExecution() {
        ToolCallback delegate = tool("project_mcp_demo_mysql_prod_readonly_mcp");
        String input = "{\"toolName\":\"mysql_health\",\"arguments\":{}}";
        when(delegate.call(input))
                .thenReturn("{\"status\":\"BLOCKED\",\"allowed\":false,\"remoteCallExecuted\":false,\"reasonCode\":\"MCP_POLICY_PENDING_REVIEW\"}");
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);
        String output = context.getTools().get(0).call(input);

        assertTrue(output.contains("MCP_POLICY_PENDING_REVIEW"));
        assertEquals(List.of("TOOL_CALL_STARTED", "TOOL_CALL_BLOCKED"),
                events.stream().map(OpsRuntimeEvent::getEventType).toList());
        assertEquals("BLOCKED", events.get(1).getStatus());
        assertTrue(events.stream().noneMatch(event -> "TOOL_CALL_FINISHED".equals(event.getEventType())));
    }

    @Test
    void failedToolMustRecordFailureAndRethrowOriginalError() {
        ToolCallback delegate = tool("unsafe_admin");
        IllegalStateException failure = new IllegalStateException("boom");
        when(delegate.call("{}")).thenThrow(failure);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        OpsRuntimeToolTraceDecorator decorator = decorator(null);

        decorator.decorate(context);
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> context.getTools().get(0).call("{}"));

        assertSame(failure, thrown);
        assertEquals(List.of("TOOL_CALL_STARTED", "TOOL_CALL_FAILED"),
                events.stream().map(OpsRuntimeEvent::getEventType).toList());
        assertEquals("boom", events.get(1).getPayload().get("error"));
    }

    @Test
    void canceledRunMustRecordSkippedAndNeverInvokeDelegate() {
        ToolCallback delegate = tool("project_mcp_logs");
        OpsRunCancellationRegistry cancellationRegistry =
                mock(OpsRunCancellationRegistry.class);
        when(cancellationRegistry.isCanceled("run-1")).thenReturn(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeResourceContext context = context(List.of(delegate), events);
        OpsRuntimeToolTraceDecorator decorator = decorator(cancellationRegistry);

        decorator.decorate(context);
        assertThrows(
                OpsRunCanceledException.class,
                () -> context.getTools().get(0).call("{}"));

        assertEquals(List.of("TOOL_CALL_SKIPPED"),
                events.stream().map(OpsRuntimeEvent::getEventType).toList());
        assertEquals("CANCELED", events.get(0).getStatus());
        assertEquals("mcp", events.get(0).getPayload().get("toolKind"));
        verify(delegate, never()).call("{}");
    }

    private OpsRuntimeToolTraceDecorator decorator(
            OpsRunCancellationRegistry cancellationRegistry) {
        return new OpsRuntimeToolTraceDecorator(() -> cancellationRegistry);
    }

    private OpsRuntimeResourceContext context(
            List<ToolCallback> tools,
            List<OpsRuntimeEvent> events) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .node(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .type("AGENT")
                        .agent("worker")
                        .build())
                .request(OpsAgentChatRequest.builder()
                        .projectId("project-1")
                        .runId("run-1")
                        .build())
                .projectId("project-1")
                .mcpIds(new java.util.LinkedHashSet<>(List.of("logs")))
                .tools(new ArrayList<>(tools))
                .events(events)
                .build();
    }

    private ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(name)
                .description(name)
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .build());
        return callback;
    }
}
