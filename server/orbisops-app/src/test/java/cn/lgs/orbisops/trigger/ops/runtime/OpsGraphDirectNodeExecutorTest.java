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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

class OpsGraphDirectNodeExecutorTest {

    @Test
    void largeBusinessEvidenceIsStoredOnceWhileCompleteToolRecordReferencesRemainAvailable() {
        var assembler = mock(OpsRuntimeResourceAssembler.class);
        var service = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var config = OpsMcpServerConfig.builder().toolId("observability").build();
        var callback = new OpsProgressiveMcpCallbackAdapter(() -> service).dispatcherCallback(config,
                List.of(Map.of("toolName", "metrics_window", "readOnly", true, "disclosureTier", "CORE")));
        String measuredSamples = "x".repeat(300_000);
        var business = Map.of("samples",measuredSamples,"sampleCount",1800);
        var envelope = Map.of("orbisopsResultVersion",1,"isError",false,"structuredContent",business,"normalizedContent",business);
        var response = new java.util.LinkedHashMap<String,Object>();
        response.putAll(Map.of("providerId","observability","remoteToolName","metrics_window","allowed",true,"decision","ALLOWED",
                "resultId","tool-result-wrapper","providerResultId","tool-result-native","fullOutputRef","db:tool-result-wrapper",
                "providerFullOutputRef","db:tool-result-native","outputHash","a".repeat(64),"providerOutputHash","b".repeat(64)));
        response.putAll(Map.of("mcpEnvelope",envelope,"normalizedContent",business,"providerResult",business));
        String original = cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringifyPreservingOrder(response);
        when(service.executeMcp(org.mockito.ArgumentMatchers.eq(config),any(String.class),org.mockito.ArgumentMatchers.eq("ops-agent"),org.mockito.ArgumentMatchers.eq(true))).thenReturn(response);
        when(assembler.assembleNode(any(),any(),any(),any(),any())).thenReturn(OpsRuntimeResourceBundle.builder().tools(List.of(callback)).build());
        var action = Map.<String,Object>of("mcpId","observability","remoteToolName","metrics_window","arguments",Map.of(),
                "structuredOutputKey","metrics","outputMode","MCP_EVIDENCE_REFERENCE");
        var node = OpsWorkflowNode.builder().nodeId("metrics").config(Map.of("actions",List.of(action))).build();
        var result = new OpsGraphDirectNodeExecutor(assembler).execute(context(node,null,"input"));
        var evidence = (Map<?,?>)result.result().get("workflowData_metrics");
        assertEquals(business,evidence.get("normalizedContent"));
        assertEquals("db:tool-result-native",evidence.get("providerFullOutputRef"));
        assertFalse(evidence.containsKey("mcpEnvelope"));
        assertTrue(result.output().length()<2000);
        assertTrue(cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringifyPreservingOrder(result.result()).length()<310_000);
        assertTrue(original.length()>1_048_576,"Regression exceeds the original 1 MiB gate like real 900s telemetry");
        assertEquals(original,cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringifyPreservingOrder(response),"Complete stored tool receipt is not mutated by projection");
        response.put("providerFullOutputRef","db:tool-result-other");
        assertThrows(IllegalArgumentException.class, () -> new OpsGraphDirectNodeExecutor(assembler).execute(context(node,null,"input")));
        response.put("providerFullOutputRef","db:tool-result-native");
        response.put("normalizedContent",Map.of("samples","x".repeat(1_048_576)));
        assertThrows(IllegalArgumentException.class, () -> new OpsGraphDirectNodeExecutor(assembler).execute(context(node,null,"input")),"Even trusted output cannot exceed the generic normalized-data budget");
    }

    @Test
    void fabricatedEvidenceEnvelopeCannotBypassGenericDataLimit() {
        var assembler = mock(OpsRuntimeResourceAssembler.class);
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name("query").description("fixture").inputSchema("{}").build());
        when(callback.call(any(String.class))).thenReturn(cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringifyPreservingOrder(
                Map.of("mcpEnvelope",Map.of("orbisopsResultVersion",1,"isError",false),"outputMode","MCP_EVIDENCE_REFERENCE","samples","x".repeat(1_048_576))));
        when(assembler.assembleNode(any(),any(),any(),any(),any())).thenReturn(OpsRuntimeResourceBundle.builder().tools(List.of(callback)).build());
        var node=OpsWorkflowNode.builder().nodeId("query").config(Map.of("actions",List.of(
                Map.of("toolName","query","arguments",Map.of(),"structuredOutputKey","evidence")))).build();
        assertThrows(IllegalArgumentException.class,()->new OpsGraphDirectNodeExecutor(assembler).execute(context(node,null,"input")));
    }

    @Test
    void subsequentActionsAndRestoredNodesCanUseTypedEvidenceWithoutFlatteningAuthority() {
        var assembler = mock(OpsRuntimeResourceAssembler.class);
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name("query")
                .description("fixture").inputSchema("{}").build());
        when(callback.call(any(String.class))).thenReturn("{\"count\":101}", "{\"decision\":\"OBSERVED\"}");
        when(assembler.assembleNode(any(), any(), any(), any(), any())).thenReturn(
                OpsRuntimeResourceBundle.builder().tools(List.of(callback)).build());
        var node = OpsWorkflowNode.builder().nodeId("metrics").config(Map.of("actions", List.of(
                Map.of("toolName", "query", "argumentBindings", Map.of("service", "input.service"), "structuredOutputKey", "metrics"),
                Map.of("toolName", "query", "argumentBindings", Map.of("sampleCount", "nodeOutput.workflowData_metrics.count",
                        "trace", "nodeOutput.workflowData_logs.trace"), "structuredOutputKey", "review")))).build();
        var prior = new com.alibaba.cloud.ai.graph.OverAllState(Map.of("workflowData_logs", Map.of("trace", "trace-a"),
                "landingApproved", true));
        var context = context(node, prior, "{\"service\":\"orders\"}");
        var result = new OpsGraphDirectNodeExecutor(assembler).execute(context);
        var inputs = ArgumentCaptor.forClass(String.class);
        verify(callback, times(2)).call(inputs.capture());
        assertEquals(Map.of("service", "orders"), JSON.parseObject(inputs.getAllValues().get(0), Map.class));
        assertEquals(Map.of("sampleCount", 101, "trace", "trace-a"), JSON.parseObject(inputs.getAllValues().get(1), Map.class));
        assertEquals(Map.of("count", 101), result.result().get("workflowData_metrics"));
        assertEquals(Map.of("decision", "OBSERVED"), result.result().get("workflowData_review"));
        assertFalse(result.result().containsKey("workflowData_logs"), "Do not re-emit another branch's state and overwrite it");
        assertFalse(result.result().containsKey("landingApproved"));
    }

    @Test
    void missingInputPreventsPhysicalInvocationAndMalformedStructuredOutputCannotContinue() {
        var assembler = mock(OpsRuntimeResourceAssembler.class);
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name("query").description("fixture").inputSchema("{}").build());
        when(assembler.assembleNode(any(), any(), any(), any(), any())).thenReturn(
                OpsRuntimeResourceBundle.builder().tools(List.of(callback)).build());
        var node = OpsWorkflowNode.builder().nodeId("read").config(Map.of("actions", List.of(
                Map.of("toolName", "query", "argumentBindings", Map.of("service", "input.service"), "structuredOutputKey", "metrics")))).build();
        var executor = new OpsGraphDirectNodeExecutor(assembler);
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context(node,null,"{}")));
        verify(callback,never()).call(any(String.class));
        when(callback.call(any(String.class))).thenReturn("not-json");
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context(node,null,"{\"service\":\"orders\"}")));
        verify(callback,times(1)).call(any(String.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MCP_POLICY_PENDING_REVIEW", "MCP_POLICY_DISABLED", "MCP_TOOL_NOT_AUTHORIZED"})
    void directEvidenceProjectionPreservesTypedBlockedReceiptInsteadOfCallingItInvalid(String code) {
        var assembler = mock(OpsRuntimeResourceAssembler.class);
        var service = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var config = OpsMcpServerConfig.builder().toolId("observability").build();
        var blocked = Map.<String,Object>of("providerId", "observability", "remoteToolName", "observe",
                "allowed", false, "status", "BLOCKED", "reasonCode", code, "remoteCallExecuted", false);
        when(service.executeMcp(org.mockito.ArgumentMatchers.eq(config), any(String.class),
                org.mockito.ArgumentMatchers.eq("ops-agent"), org.mockito.ArgumentMatchers.eq(true)))
                .thenThrow(new cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.ToolBlockedException(
                        "private args=secret-value", blocked));
        var callback = new OpsProgressiveMcpCallbackAdapter(() -> service).dispatcherCallback(config,
                List.of(Map.of("toolName", "observe", "readOnly", true)));
        when(assembler.assembleNode(any(),any(),any(),any(),any()))
                .thenReturn(OpsRuntimeResourceBundle.builder().tools(List.of(callback)).build());
        var action = Map.<String,Object>of("mcpId", "observability", "remoteToolName", "observe",
                "arguments", Map.of(), "structuredOutputKey", "state", "outputMode", "MCP_EVIDENCE_REFERENCE");
        var node = OpsWorkflowNode.builder().nodeId("observe").config(Map.of("actions",List.of(action))).build();
        var denied = assertThrows(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.ToolBlockedException.class,
                () -> new OpsGraphDirectNodeExecutor(assembler).execute(context(node, null, "input")));
        assertEquals(false, denied.envelope().get("allowed"));
        assertEquals(code, denied.envelope().get("reasonCode"));
        assertEquals(false, denied.envelope().get("remoteCallExecuted"));
        assertEquals(false, blocked.get("allowed"), "Projection must not modify the native denial");
    }

    private OpsGraphNodeExecutionContext context(OpsWorkflowNode node,
            com.alibaba.cloud.ai.graph.OverAllState state, String query) {
        return new OpsGraphNodeExecutionContext(OpsAgentDefinition.builder().agentId("workflow").build(), node,
                OpsAgentChatRequest.builder().query(query).projectId("project").runId("run").build(), state,
                new ArrayList<>(), null, null, "DIRECT", "", null, query, "", 0L, 0L, false);
    }

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
