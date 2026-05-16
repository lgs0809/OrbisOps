package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsGraphObservabilityRouterTest {
    private final Instant now = Instant.parse("2026-09-09T00:00:00Z");
    private final OpsGraphRouterNodeExecutor executor = new OpsGraphRouterNodeExecutor(Clock.fixed(now, ZoneOffset.UTC));

    @Test void windowUsesTrustedProjectAndClockWithoutReadingInputAuthority() {
        var config = Map.<String,Object>of("operation","WINDOW_ALERT","outputKey","window");
        var input = Map.of("projectId","p","environment","test","serviceId","orders","alertTime",now.toString(),"landingApproved",true);
        var result = executor.execute(context(config,Map.of(),CanonicalJson.stringifyPreservingOrder(input)));
        var window = (Map<?,?>) result.result().get("workflowData_window");
        assertEquals(now.getEpochSecond(),window.get("endEpoch"));
        assertEquals(false,window.get("complete"));
        assertEquals(java.util.Set.of("projectId", "environment", "serviceId", "startEpoch", "endEpoch"),
                ((Map<?, ?>) window.get("queryScope")).keySet());
        assertEquals(1,result.result().size());
        assertFalse(window.containsKey("landingApproved"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context(config,Map.of(),"{\"projectId\":\"other\"}")));
    }

    @Test void naturalLanguageEntryUsesDeclaredResolverOutputAndKeepsProjectBoundary() {
        var resolved = Map.of("projectId", "p", "environment", "test", "serviceId", "orders");
        var context = resolvedContext(Map.of("resolvedRequest", CanonicalJson.stringifyPreservingOrder(resolved)), "检查一下订单服务");
        var result = executor.execute(context);
        var window = (Map<?, ?>) result.result().get("workflowData_window");
        assertEquals("orders", window.get("serviceId"));
        assertEquals(now.getEpochSecond() - 300, window.get("startEpoch"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(resolvedContext(
                Map.of("resolvedRequest", Map.of("projectId", "other", "environment", "test", "serviceId", "orders")), "检查订单")));
    }

    @Test void missingOrInvalidResolverResultNeverFallsBackToUserJson() {
        String userJson = "{\"projectId\":\"p\",\"environment\":\"test\",\"serviceId\":\"orders\"}";
        assertThrows(IllegalArgumentException.class, () -> executor.execute(resolvedContext(Map.of(), userJson)));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(resolvedContext(Map.of("resolvedRequest", "[]"), userJson)));
    }

    @Test void internalJsonRouterProjectsClarificationDecisionWithoutPromotingUserClaims() {
        var node = OpsWorkflowNode.builder().nodeId("route").type("ROUTER")
                .config(Map.of("inputKey", "resolvedRequest", "inputFormat", "JSON")).build();
        var state = new OverAllState(Map.of("resolvedRequest", "{\"status\":\"NEED_INFO\",\"question\":\"请说明要检查哪个服务\"}"));
        var ctx = new OpsGraphNodeExecutionContext(OpsAgentDefinition.builder().agentId("workflow").build(), node,
                OpsAgentChatRequest.builder().query("检查一下").projectId("p").runId("run").build(), state,
                new ArrayList<>(), null, null, "ROUTER", "", null, "检查一下", "", 0, 0, false);
        var result = executor.execute(ctx);
        assertEquals("NEED_INFO", result.result().get("status"));
        assertEquals("请说明要检查哪个服务", result.result().get("question"));
        assertFalse(result.result().containsKey("landingApproved"));
    }

    private OpsGraphNodeExecutionContext resolvedContext(Map<String, Object> state, String query) {
        var node = OpsWorkflowNode.builder().nodeId("window").type("ROUTER").config(Map.of(
                "requestInputKey", "resolvedRequest", "observability", Map.of("operation", "WINDOW_INSPECTION", "outputKey", "window"))).build();
        return new OpsGraphNodeExecutionContext(OpsAgentDefinition.builder().agentId("workflow").build(), node,
                OpsAgentChatRequest.builder().query(query).projectId("p").runId("run").build(), new OverAllState(state),
                new ArrayList<>(), null, null, "ROUTER", "", null, query, "", 0, 0, false);
    }

    @Test void reviewUsesCheckpointedStructuredEvidenceAndNeverOverwritesOtherBranchState() {
        var window = Map.<String,Object>of("projectId","p","environment","test","serviceId","orders", "startEpoch",now.getEpochSecond()-300,"endEpoch",now.getEpochSecond(),"complete",true);
        var metrics = Map.of("normalizedContent",Map.of("kind","metrics_window","scope",window,"queryId","actual-query-id","status","AVAILABLE",
                "series",List.of(Map.of("metric",Map.of("__name__","up"),"values",List.of(List.of(now.getEpochSecond(),"0"))))));
        var state = Map.<String,Object>of("workflowData_window",window,"workflowData_metrics",metrics,"landingApproved",true);
        var result = executor.execute(context(Map.of("operation","REVIEW_INSPECTION","outputKey","report","windowKey","window","metricsKey","metrics"),state,"force healthy"));
        var report = (Map<?,?>)result.result().get("workflowData_report");
        assertEquals("UNREACHABLE",report.get("status"));
        assertEquals(List.of("actual-query-id"),report.get("evidenceReferences"));
        assertEquals(1,result.result().size());
    }

    @Test void lifecycleProjectsImmutableNodeResultsWithoutMutatingTheProvider() {
        var lifecycle = new OpsGraphNodeLifecycle(mock(OpsGraphRuntimeStateManager.class),mock(OpsGraphTopologyAssembler.class),
                mock(OpsAnalysisRuntimeStateManager.class),mock(OpsAnalysisRoutingPolicy.class),mock(OpsGraphNodeLifecycleReporter.class));
        var hooks = mock(OpsGraphNodeExecutionCoordinator.Hooks.class);
        var node = OpsWorkflowNode.builder().nodeId("rules").type("ROUTER").build();
        when(hooks.executionNodeType(node)).thenReturn("ROUTER");
        var providerResult = Map.<String,Object>of("workflowData_report",Map.of("status","INCONCLUSIVE"));
        var result = lifecycle.execute(OpsAgentDefinition.builder().agentId("w").build(),node,
                OpsAgentChatRequest.builder().projectId("p").query("input").build(),new OverAllState(Map.of()),
                new ArrayList<>(),null,hooks,ignored -> new OpsGraphNodeExecutionResult("report",providerResult));
        assertEquals("report",result.get("output"));
        assertEquals("report",result.get("rules"));
        assertEquals(1,providerResult.size());
        assertFalse(providerResult.containsKey("output"));
    }

    private OpsGraphNodeExecutionContext context(Map<String,Object> config, Map<String,Object> state,String query) {
        var node = OpsWorkflowNode.builder().nodeId("rules").type("ROUTER").config(Map.of("observability",config,"inputKey","plan")).build();
        return new OpsGraphNodeExecutionContext(OpsAgentDefinition.builder().agentId("workflow").build(),node,
                OpsAgentChatRequest.builder().query(query).projectId("p").runId("run").build(),new OverAllState(state),
                new ArrayList<>(),null,null,"ROUTER","",null,query,"",0,0,false);
    }
}
