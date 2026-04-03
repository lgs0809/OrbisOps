package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the actual Spring AI Alibaba graph, including nested conditional routing. */
class OpsGraphBusinessCompletionTest {
    private static final List<String> PATH = List.of("start","window","metrics","logs","review","sql","sql-review","explain","report","end");

    @ParameterizedTest @ValueSource(strings={"single","FIRST_MATCH","FAN_OUT"})
    void exclusiveBusinessBranchesReachFinalReviewAndEnd(String mode) throws Exception {
        var fixture = fixture(false);
        String output = fixture.coordinator.execute(definition(mode),request(),new ArrayList<>(),null,mock(OpsGraphNodeExecutionCoordinator.Hooks.class));
        assertEquals(PATH,fixture.visited);
        assertEquals("end",output);
    }

    @Test void libraryReturningBeforeDeclaredTerminalCannotBecomeSuccessfulRun() throws Exception {
        var fixture = fixture(true);
        var definition = definition("FAN_OUT");
        var error = assertThrows(IllegalStateException.class, () -> fixture.coordinator.execute(
                definition,request(),new ArrayList<>(),null,mock(OpsGraphNodeExecutionCoordinator.Hooks.class)));
        assertTrue(error.getMessage().contains("WORKFLOW_TERMINAL_NODE_NOT_REACHED"),error.getMessage());
        assertFalse(fixture.visited.contains("end"));
        verify(fixture.analysis,never()).publishRunFinished(eq(definition),isNull(),eq("SUCCEEDED"),anyString());
    }

    private Fixture fixture(boolean incompleteTopology) throws Exception {
        var state = new OpsGraphRuntimeStateManager();
        var analysis = mock(OpsAnalysisRuntimeStateManager.class);
        var routing = new OpsAnalysisRoutingPolicy();
        var topology = spy(OpsGraphTopologyAssemblerTestFactory.create(routing,new OpsGraphConditionEvaluator(),state));
        if (incompleteTopology) doAnswer(call -> {
            com.alibaba.cloud.ai.graph.StateGraph graph = call.getArgument(0);
            graph.addEdge(com.alibaba.cloud.ai.graph.StateGraph.START,"start");
            graph.addEdge("start",com.alibaba.cloud.ai.graph.StateGraph.END);
            return null;
        }).when(topology).addEdges(any(),any(),any(),any(),any());
        var bodies = mock(OpsGraphNodeExecutionCoordinator.class);
        var visited = new CopyOnWriteArrayList<String>();
        when(bodies.execute(any(),any(),any(),any(),any(),any(),any())).thenAnswer(call -> {
            OpsWorkflowNode node = call.getArgument(1); visited.add(node.getNodeId());
            return Map.of("takeBranch",true,"output",node.getNodeId());
        });
        var journal = new OpsRuntimeEventJournal(mock(OpsWorkSessionRunAdapter.class),mock(GraphEventApplicationService.class),()->null);
        var coordinator = new OpsGraphEngineExecutionCoordinator(state,analysis,()->null,bodies,topology,
                mock(OpsRuntimeConversationContextCoordinator.class),routing,mock(OpsRuntimeSkillLearningCoordinator.class),journal,
                new OpsRuntimeNodeExecutionPolicy(routing),Runnable::run);
        return new Fixture(coordinator,visited,analysis);
    }

    private OpsAgentDefinition definition(String mode) {
        var nodes = PATH.stream().map(id -> OpsWorkflowNode.builder().nodeId(id).agent("fixture")
                .type(id.equals("start") ? "START" : id.equals("end") ? "END" : "ROUTER")
                .config(Map.of("routeMode",mode)).build()).toList();
        var edges = new ArrayList<OpsGraphEdge>();
        for(int i=0;i<PATH.size()-1;i++) {
            String from=PATH.get(i),to=PATH.get(i+1);
            boolean branch=from.equals("review") || from.equals("sql-review");
            edges.add(OpsGraphEdge.builder().from(from).to(to).conditionType(branch ? "expression" : "always")
                    .condition(branch ? "nodeOutput.takeBranch == true" : "always").build());
            if(branch) edges.add(OpsGraphEdge.builder().from(from).to("end").conditionType("default").condition("default").build());
        }
        return OpsAgentDefinition.builder().agentId("business-completion").projectId("p").startNodeId("start")
                .definitionKind("SPECIALIZED_WORKFLOW").nodes(nodes).edges(edges).build();
    }
    private OpsAgentChatRequest request() { return OpsAgentChatRequest.builder().projectId("p").runId("run").sessionId("session").query("fixture").build(); }
    private record Fixture(OpsGraphEngineExecutionCoordinator coordinator,List<String> visited,OpsAnalysisRuntimeStateManager analysis) {}
}
