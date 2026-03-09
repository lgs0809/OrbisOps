package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphTopologyAssemblerTest {

    private final OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
    private final OpsGraphTopologyAssembler assembler = OpsGraphTopologyAssemblerTestFactory.create(
            routingPolicy,
            new OpsGraphConditionEvaluator(),
            new OpsGraphRuntimeStateManager());

    @Test
    void feedbackLoopRoundAndBudgetAreBoundedByDefinition() {
        OpsGraphEdge feedback = OpsGraphEdge.builder()
                .from("review")
                .to("elasticsearch")
                .conditionType("review_decision")
                .condition("needs:logs")
                .feedback(true)
                .build();
        OpsLoopPolicy loop = OpsLoopPolicy.builder()
                .loopId("investigation")
                .feedbackEdges(List.of("review->elasticsearch"))
                .maxRounds(2)
                .exitEdge("review->end")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .defaultMaxMainRounds(6)
                .edges(List.of(feedback,
                        OpsGraphEdge.builder().from("review").to("end").conditionType("default").build()))
                .loops(List.of(loop))
                .build();
        OverAllState firstRound = new OverAllState(Map.of(
                OpsGraphRuntimeStateManager.LOOP_ROUNDS_KEY, Map.of("investigation", 1)));
        OverAllState exhausted = new OverAllState(Map.of(
                OpsGraphRuntimeStateManager.LOOP_ROUNDS_KEY, Map.of("investigation", 2)));

        OpsGraphTopologyAssembler.LoopRoundStatus status =
                assembler.primaryFeedbackLoopRoundStatus(definition, new OpsAgentRunRequestDTO(), firstRound);

        assertTrue(assembler.feedbackLoopEnabled(definition));
        assertTrue(assembler.feedbackLoopForEdge(definition, feedback).isPresent());
        assertEquals(2, assembler.effectiveLoopMaxRounds(loop, definition, new OpsAgentRunRequestDTO()));
        assertEquals("investigation", status.loopId());
        assertEquals(1, status.currentRound());
        assertEquals(2, status.maxRounds());
        assertEquals(2, status.nextRound());
        assertFalse(status.exhausted());
        assertFalse(assembler.allFeedbackLoopsExhausted(definition, new OpsAgentRunRequestDTO(), firstRound));
        assertTrue(assembler.allFeedbackLoopsExhausted(definition, new OpsAgentRunRequestDTO(), exhausted));
    }

    @Test
    void activeEdgesAndRecursionBudgetRemainDeterministic() {
        OpsGraphEdge always = OpsGraphEdge.builder()
                .from("start")
                .to("router")
                .conditionType("always")
                .condition("always")
                .build();
        OpsGraphEdge route = OpsGraphEdge.builder()
                .from("router")
                .to("elasticsearch")
                .conditionType("route_match")
                .condition("logs")
                .build();
        List<OpsWorkflowNode> nodes = List.of(
                OpsWorkflowNode.builder().nodeId("start").type("START").build(),
                OpsWorkflowNode.builder().nodeId("router").type("ROUTER").build(),
                OpsWorkflowNode.builder().nodeId("elasticsearch").type("AGENT").build());
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .nodes(nodes)
                .edges(List.of(always, route))
                .defaultMaxMainRounds(3)
                .build();
        OverAllState state = new OverAllState(Map.of("selectedRoutes", List.of("elasticsearch")));

        assertTrue(assembler.edgeActiveForPrompt(always, state));
        assertTrue(assembler.edgeActiveForPrompt(route, state));
        assertTrue(assembler.recursionLimit(definition, new OpsAgentRunRequestDTO(), nodes) > nodes.size());
        assertTrue(assembler.marksGraphCompletedOnSuccess("REPORT"));
        assertFalse(assembler.marksGraphCompletedOnSuccess("ROUTER"));
        assertEquals("review->elasticsearch", assembler.graphEdgeKey(" review ", " elasticsearch "));
    }

    @Test
    void runtimeCannotReclaimTopologyCompilationAndFeedbackLoopResponsibilities() {
        Set<String> forbiddenMethods = Set.of(
                "addGraphEdges",
                "parallelJoins",
                "graphRecursionLimit",
                "feedbackLoopForEdge",
                "routeConditions",
                "routeCondition",
                "loopRound",
                "incrementLoopRound",
                "loopExitTarget",
                "edgeActiveForPrompt",
                "graphRoutingChoices",
                "edgeRuntimeMetadata",
                "marksGraphCompletedOnSuccess");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredClasses())
                .anyMatch(type -> Set.of("ParallelJoin", "LoopRoundStatus").contains(type.getSimpleName())));
    }
}
