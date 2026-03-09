package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.NodeAggregationStrategy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncMultiCommandAction;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.assertj.core.api.Assertions.assertThat;

class OpsParallelGraphJoinTest {

    @Test
    void shouldJoinOnlySelectedParallelBranchesOnce() throws Exception {
        AtomicInteger branchA = new AtomicInteger();
        AtomicInteger branchB = new AtomicInteger();
        AtomicInteger branchC = new AtomicInteger();
        AtomicInteger joined = new AtomicInteger();
        StateGraph graph = new StateGraph("parallel-join", () -> Map.of(
                "output", KeyStrategy.REPLACE));

        graph.addNode("router", node_async(state -> Map.of()));
        graph.addNode("a", node_async(state -> {
            branchA.incrementAndGet();
            return Map.of("output", "a");
        }));
        graph.addNode("b", node_async(state -> {
            branchB.incrementAndGet();
            return Map.of("output", "b");
        }));
        graph.addNode("c", node_async(state -> {
            branchC.incrementAndGet();
            return Map.of("output", "c");
        }));
        graph.addNode("join", node_async(state -> {
            joined.incrementAndGet();
            return Map.of("output", "joined");
        }));

        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("a", "a");
        routes.put("b", "b");
        routes.put("c", "c");
        graph.addEdge(START, "router");
        graph.addParallelConditionalEdges(
                "router",
                AsyncMultiCommandAction.of(state -> CompletableFuture.completedFuture(List.of("a", "b"))),
                routes);
        graph.addEdge(List.of("a", "b", "c"), "join");
        graph.addEdge("join", END);

        CompiledGraph compiledGraph = graph.compile();
        compiledGraph.invoke(Map.of(), RunnableConfig.builder()
                .defaultParallelAggregationStrategy(NodeAggregationStrategy.ALL_OF)
                .build());

        assertThat(branchA).hasValue(1);
        assertThat(branchB).hasValue(1);
        assertThat(branchC).hasValue(0);
        assertThat(joined).hasValue(1);
    }

    @Test
    void shouldCompileCommonBranchTargetAsSingleJoin() throws Exception {
        AtomicInteger prometheus = new AtomicInteger();
        AtomicInteger elasticsearch = new AtomicInteger();
        AtomicInteger rag = new AtomicInteger();
        AtomicInteger review = new AtomicInteger();
        List<OpsWorkflowNode> nodes = List.of(
                OpsWorkflowNode.builder().nodeId("start").type("START").build(),
                OpsWorkflowNode.builder()
                        .nodeId("router")
                        .type("ROUTER")
                        .config(Map.of("routeMode", "multi"))
                        .build(),
                OpsWorkflowNode.builder().nodeId("prometheus").type("AGENT").build(),
                OpsWorkflowNode.builder().nodeId("elasticsearch").type("AGENT").build(),
                OpsWorkflowNode.builder().nodeId("rag").type("AGENT").build(),
                OpsWorkflowNode.builder().nodeId("review").type("AGENT").build(),
                OpsWorkflowNode.builder().nodeId("end").type("END").build());
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .startNodeId("start")
                .nodes(nodes)
                .edges(List.of(
                        edge("start", "router", "always", null),
                        edge("router", "prometheus", "prometheus", "route_match"),
                        edge("router", "elasticsearch", "elasticsearch", "route_match"),
                        edge("router", "rag", "rag", "route_match"),
                        edge("prometheus", "review", "always", null),
                        edge("elasticsearch", "review", "always", null),
                        edge("rag", "review", "always", null)))
                .build();
        StateGraph graph = new StateGraph("runtime-parallel-join", () -> Map.of(
                "selectedRoutes", KeyStrategy.REPLACE,
                "output", KeyStrategy.REPLACE));
        graph.addNode("start", node_async(state -> Map.of()));
        graph.addNode("router", node_async(state -> Map.of(
                "selectedRoutes", List.of("prometheus", "elasticsearch"))));
        graph.addNode("prometheus", node_async(state -> {
            prometheus.incrementAndGet();
            return Map.of();
        }));
        graph.addNode("elasticsearch", node_async(state -> {
            elasticsearch.incrementAndGet();
            return Map.of();
        }));
        graph.addNode("rag", node_async(state -> {
            rag.incrementAndGet();
            return Map.of();
        }));
        graph.addNode("review", node_async(state -> {
            review.incrementAndGet();
            return Map.of("output", "reviewed");
        }));
        graph.addNode("end", node_async(state -> Map.of()));

        topologyAssembler().addEdges(
                graph, definition, new OpsAgentRunRequestDTO(), null, nodes);
        graph.compile().invoke(Map.of(), RunnableConfig.builder()
                .defaultParallelAggregationStrategy(NodeAggregationStrategy.ALL_OF)
                .build());

        assertThat(prometheus).hasValue(1);
        assertThat(elasticsearch).hasValue(1);
        assertThat(rag).hasValue(0);
        assertThat(review).hasValue(1);
    }

    private OpsGraphEdge edge(String from, String to, String condition, String conditionType) {
        return OpsGraphEdge.builder()
                .from(from)
                .to(to)
                .condition(condition)
                .conditionType(conditionType)
                .build();
    }

    private OpsGraphTopologyAssembler topologyAssembler() {
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        return OpsGraphTopologyAssemblerTestFactory.create(
                routingPolicy,
                new OpsGraphConditionEvaluator(),
                new OpsGraphRuntimeStateManager());
    }
}
