package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphFeedbackLoopPolicyTest {

    private final OpsGraphFeedbackLoopPolicy policy =
            new OpsGraphFeedbackLoopPolicy(
                    new OpsAnalysisRoutingPolicy(),
                    new OpsGraphConditionEvaluator());

    @Test
    void parallelFeedbackEdgesInSameLoopMustConsumeOnlyOneRound() {
        OpsGraphEdge logs = feedback("review", "logs", "needs:logs");
        OpsGraphEdge metrics = feedback("review", "metrics", "needs:metrics");
        OpsLoopPolicy loop = OpsLoopPolicy.builder()
                .loopId("investigation")
                .feedbackEdges(List.of("review->logs", "review->metrics"))
                .maxRounds(3)
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .edges(List.of(logs, metrics))
                .loops(List.of(loop))
                .build();
        OverAllState state = new OverAllState(Map.of());

        OpsGraphFeedbackLoopPolicy.Selection selection = policy.selectRoutes(
                definition,
                new OpsAgentRunRequestDTO(),
                state,
                List.of(logs, metrics));

        assertEquals(List.of("needs:logs", "needs:metrics"),
                selection.selectedConditions());
        assertEquals(1, policy.round(state, "investigation"));
    }

    @Test
    void exhaustedLoopMustSelectConfiguredExitConditionWithoutIncrementing() {
        OpsGraphEdge feedback = feedback(
                "review", "logs", "needs:logs");
        OpsGraphEdge exit = OpsGraphEdge.builder()
                .from("review")
                .to("report")
                .conditionType("default")
                .build();
        OpsLoopPolicy loop = OpsLoopPolicy.builder()
                .loopId("investigation")
                .feedbackEdges(List.of("review->logs"))
                .exitEdge("review->report")
                .maxRounds(2)
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .edges(List.of(feedback, exit))
                .loops(List.of(loop))
                .build();
        OverAllState state = new OverAllState(Map.of(
                OpsGraphRuntimeStateManager.LOOP_ROUNDS_KEY,
                Map.of("investigation", 2)));

        OpsGraphFeedbackLoopPolicy.Selection selection = policy.selectRoutes(
                definition,
                new OpsAgentRunRequestDTO(),
                state,
                List.of(feedback));

        assertTrue(selection.selectedConditions().isEmpty());
        assertEquals("__loop_exit__:investigation",
                selection.exhaustedExitCondition());
        assertEquals(2, policy.round(state, "investigation"));
    }

    @Test
    void requestRoundLimitMustOverrideLoopAndDefinitionDefaultsWithinBound() {
        OpsLoopPolicy loop = OpsLoopPolicy.builder()
                .loopId("investigation")
                .maxRounds(7)
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .defaultMaxMainRounds(6)
                .build();
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .maxRounds(20)
                .build();

        assertEquals(20,
                policy.effectiveMaxRounds(loop, definition, request));
    }

    private OpsGraphEdge feedback(
            String from,
            String to,
            String condition) {
        return OpsGraphEdge.builder()
                .from(from)
                .to(to)
                .conditionType("review_decision")
                .condition(condition)
                .feedback(true)
                .build();
    }
}
