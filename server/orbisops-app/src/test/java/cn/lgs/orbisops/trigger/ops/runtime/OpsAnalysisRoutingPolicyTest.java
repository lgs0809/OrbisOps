package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisRoutingPolicyTest {

    private final OpsAnalysisRoutingPolicy policy = new OpsAnalysisRoutingPolicy();

    @Test
    void normalizesSourcesAndProjectsGraphTasksFromIncomingRoutes() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("es-node")
                .type("ELASTICSEARCH")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .nodes(List.of(node))
                .edges(List.of(OpsGraphEdge.builder()
                        .from("router")
                        .to("es-node")
                        .conditionType("route_match")
                        .condition("needs:logs")
                        .build()))
                .build();

        assertEquals("elasticsearch", policy.normalizeSource("logs"));
        assertEquals("elasticsearch", policy.incomingRouteKey(definition, node));
        assertEquals("elasticsearch", policy.resolveSource(definition, node, "ELASTICSEARCH"));

        OpsAnalysisResponseDTO.InvestigationTaskDTO task =
                policy.graphNodeTask(node, "ELASTICSEARCH", "elasticsearch");
        assertEquals("elasticsearch", task.getSource());
        assertEquals("es-log-agent", task.getAgent());
        assertEquals("graph.edge", task.getCondition());
    }

    @Test
    void routerSelectionHonorsAllowedExcludedAndSingleRouteMode() {
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .config(new LinkedHashMap<>(Map.of(
                        "inputKey", "selectedRoutes",
                        "routeMode", "single")))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .nodes(List.of(router))
                .edges(List.of(
                        OpsGraphEdge.builder().from("router").to("es").condition("needs:logs").build(),
                        OpsGraphEdge.builder().from("router").to("prom").condition("needs:metrics").build()))
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .metadata(new LinkedHashMap<>(Map.of(
                        "allowedInvestigationSources", List.of("logs", "metrics"),
                        "excludedCapabilities", List.of("metrics"))))
                .build();
        OverAllState graphState = new OverAllState(Map.of(
                "selectedRoutes", List.of("logs", "metrics")));

        List<String> selected = policy.selectedRoutesForRouter(
                definition, router, graphState, null, request);

        assertEquals(List.of("elasticsearch"), selected);
    }

    @Test
    void legacyIntentMetadataCannotNarrowActiveAnalysisRouting() {
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .metadata(new LinkedHashMap<>(Map.of(
                        "_intentAllowedInvestigationSources", List.of("logs"),
                        "intentDecision", Map.of("slots", Map.of(
                                "excludedCapabilities", List.of("metrics"))))))
                .build();

        assertTrue(policy.routeConstraint(request, "allowedInvestigationSources").isEmpty());
        assertTrue(policy.routeConstraint(request, "excludedCapabilities").isEmpty());
    }

    @Test
    void reviewModeAndRoundBudgetRemainBounded() {
        OpsWorkflowNode review = OpsWorkflowNode.builder()
                .nodeId("review")
                .type("REVIEW")
                .config(new LinkedHashMap<>(Map.of("reviewMode", "overall")))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .nodes(List.of(review))
                .defaultMaxMainRounds(20)
                .build();
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        request.setMaxRounds(12);

        assertEquals(OpsAnalysisRoutingPolicy.REVIEW_MODE_BATCH, policy.reviewMode(definition));
        assertEquals(8, policy.maxMainRounds(definition, request));
        assertEquals("needs:elasticsearch,replan_required",
                policy.reviewDecisionForRoutes(List.of("logs", "replan_required")));
    }

    @Test
    void runtimeCannotReclaimMigratedAnalysisStateAndRoutingResponsibilities() {
        Set<String> forbiddenMethods = Set.of(
                "analysisNodeBySource",
                "appendPromptSegment",
                "defaultAnalysisAgent",
                "normalizeReviewMode",
                "awaitAnalysisGraphSubAgent",
                "blockedAnalysisGraphExecution",
                "analysisTaskTrace");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> declaredFields = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(declaredFields.contains("graphAnalysisRuntimeStates"));
        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredClasses())
                .anyMatch(type -> "AnalysisRuntimeState".equals(type.getSimpleName())));
    }
}
