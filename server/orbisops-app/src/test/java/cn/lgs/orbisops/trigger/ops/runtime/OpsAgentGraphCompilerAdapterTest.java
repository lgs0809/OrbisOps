package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.graph.AgentGraphModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentGraphCompilerAdapterTest {

    private final OpsAgentGraphCompilerAdapter adapter = new OpsAgentGraphCompilerAdapter();

    @Test
    void legacyDefinitionCompilesAgainstRuntimeFallbackNode() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("legacy-agent")
                .build();
        OpsWorkflowNode runtimeChat = OpsWorkflowNode.builder()
                .nodeId("chat")
                .type("CHAT")
                .build();

        AgentGraphModel.Compiled compiled = adapter.compile(definition, List.of(runtimeChat));

        assertEquals("chat", compiled.startNodeId());
        assertEquals(List.of("chat"), compiled.reachableNodeIds());
        assertEquals(List.of("chat"), compiled.terminalNodeIds());
    }

    @Test
    void conditionalNodeWithoutExplicitDefaultGetsSyntheticRuntimeEnd() {
        OpsWorkflowNode router = node("router", "ROUTER");
        OpsWorkflowNode worker = node("worker", "CHAT");
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("implicit-default-agent")
                .startNodeId("router")
                .nodes(List.of(router, worker))
                .edges(List.of(OpsGraphEdge.builder()
                        .edgeId("route-worker")
                        .from("router")
                        .to("worker")
                        .conditionType("expression")
                        .condition("#decision == 'worker'")
                        .build()))
                .build();

        AgentGraphModel.Compiled compiled = adapter.compile(definition, definition.getNodes());

        assertTrue(compiled.reachableNodeIds().contains("worker"));
        assertTrue(compiled.terminalNodeIds().stream().anyMatch(id -> id.startsWith("__runtime_end__")));
    }

    @Test
    void explicitEndNodeIsReachableThroughRuntimeEndRoute() {
        OpsWorkflowNode router = node("router", "ROUTER");
        OpsWorkflowNode worker = node("worker", "CHAT");
        OpsWorkflowNode end = node("finish", "END");
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("explicit-end-agent")
                .startNodeId("router")
                .nodes(List.of(router, worker, end))
                .edges(List.of(OpsGraphEdge.builder()
                        .edgeId("route-worker")
                        .from("router")
                        .to("worker")
                        .conditionType("route_match")
                        .condition("worker")
                        .build()))
                .build();

        AgentGraphModel.Compiled compiled = adapter.compile(definition, definition.getNodes());

        assertTrue(compiled.reachableNodeIds().contains("finish"));
        assertTrue(compiled.terminalNodeIds().contains("finish"));
    }

    @Test
    void knownButDisabledNodeTypeMustFailBeforeCompilation() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("disabled-node-agent")
                .nodes(List.of(node("parallel", "PARALLEL")))
                .build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.compile(definition, definition.getNodes()));

        assertEquals("WORKFLOW_NODE_TYPE_NOT_ENABLED:PARALLEL", error.getMessage());
    }

    @Test
    void unknownNodeTypeMustNeverDegradeToChat() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("unknown-node-agent")
                .nodes(List.of(node("magic", "MAGIC_NODE")))
                .build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.compile(definition, definition.getNodes()));

        assertEquals("WORKFLOW_NODE_TYPE_UNKNOWN:MAGIC_NODE", error.getMessage());
    }

    @Test
    void unknownEdgeTargetFailsClosed() {
        OpsWorkflowNode router = node("router", "ROUTER");
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("invalid-agent")
                .startNodeId("router")
                .nodes(List.of(router))
                .edges(List.of(OpsGraphEdge.builder()
                        .edgeId("missing-target")
                        .from("router")
                        .to("missing")
                        .conditionType("always")
                        .condition("always")
                        .build()))
                .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adapter.compile(definition, definition.getNodes()));

        assertTrue(error.getMessage().contains("AGENT_GRAPH_EDGE_NODE_UNKNOWN"));
    }

    private OpsWorkflowNode node(String nodeId, String type) {
        return OpsWorkflowNode.builder().nodeId(nodeId).type(type).build();
    }
}
