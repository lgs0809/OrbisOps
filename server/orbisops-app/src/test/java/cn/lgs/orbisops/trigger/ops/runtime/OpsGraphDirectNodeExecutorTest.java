package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsGraphDirectNodeExecutorTest {

    @Test
    void progressiveMcpActionMustRouteThroughProjectDispatcher() {
        OpsRuntimeResourceAssembler assembler = mock(OpsRuntimeResourceAssembler.class);
        ToolCallback dispatcher = mock(ToolCallback.class);
        when(dispatcher.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("project_mcp_demo_project_rabbitmq_prod_readonly_mcp")
                .description("dispatcher")
                .inputSchema("{\"type\":\"object\"}")
                .build());
        when(dispatcher.call(any(String.class))).thenReturn("{\"status\":\"UP\"}");
        when(assembler.assembleNode(any(), any(), any(), any(), any())).thenReturn(
                OpsRuntimeResourceBundle.builder().tools(List.of(dispatcher)).build());

        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("direct")
                .type("AGENT")
                .mode("direct")
                .agent("direct-agent")
                .mcpIds(List.of("demo-project-rabbitmq-prod-readonly-mcp"))
                .config(Map.of(
                        "mode", "direct",
                        "actions", List.of(Map.of(
                                "mcpId", "demo-project-rabbitmq-prod-readonly-mcp",
                                "remoteToolName", "rabbitmq_health",
                                "arguments", Map.of("verbose", true),
                                "outputKey", "health"))))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("workflow")
                .projectId("demo-project")
                .nodes(List.of(node))
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .runId("run-1")
                .build();
        OpsGraphNodeExecutionContext context = new OpsGraphNodeExecutionContext(
                definition,
                node,
                request,
                null,
                new ArrayList<>(),
                null,
                null,
                "AGENT",
                "",
                null,
                "",
                "",
                0L,
                0L,
                false);

        OpsGraphNodeExecutionResult result = new OpsGraphDirectNodeExecutor(assembler).execute(context);

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).call(input.capture());
        Map<String, Object> routed = JSON.parseObject(input.getValue(), Map.class);
        assertEquals("rabbitmq_health", routed.get("toolName"));
        assertEquals(Map.of("verbose", true), routed.get("arguments"));
        assertEquals("{\"status\":\"UP\"}", result.output());
        assertEquals("{\"status\":\"UP\"}", result.result().get("health"));
    }
}
