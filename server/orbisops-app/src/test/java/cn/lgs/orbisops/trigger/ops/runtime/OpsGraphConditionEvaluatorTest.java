package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphConditionEvaluatorTest {

    private final OpsGraphConditionEvaluator evaluator = new OpsGraphConditionEvaluator();
    private final OpsGraphConditionEvaluator.Context context = new OpsGraphConditionEvaluator.Context() {
        @Override
        public String normalizeAnalysisSource(String source) {
            if (source == null) return "";
            String normalized = source.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            return switch (normalized) {
                case "logs", "log", "elastic" -> "es";
                case "metrics", "metric" -> "prom";
                case "knowledge", "vector" -> "rag";
                default -> normalized;
            };
        }

        @Override
        public String routeConditionSource(OpsGraphEdge edge) {
            String condition = edge == null || edge.getCondition() == null ? "" : edge.getCondition().trim();
            return normalizeAnalysisSource(condition.startsWith("needs:")
                    ? condition.substring("needs:".length()) : condition);
        }

        @Override
        public boolean isInvestigationRoute(String source) {
            return Set.of("rag", "es", "prom", "mysql_slow_sql").contains(normalizeAnalysisSource(source));
        }

        @Override
        public boolean hasSelectedAnalysisTask(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                                               String source) {
            return plan != null && plan.getTasks() != null && plan.getTasks().stream()
                    .anyMatch(task -> normalizeAnalysisSource(task.getSource())
                            .equals(normalizeAnalysisSource(source)));
        }
    };

    @Test
    void legacyRuntimeNoLongerOwnsCoreConditionImplementations() {
        Set<String> migratedMethods = Set.of("routeMatches", "spelConditionMatches", "isConditionalCondition");

        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .anyMatch(migratedMethods::contains));
    }

    @Test
    void routeMatchRespectsIntentAllowAndExcludeConstraints() {
        OpsGraphEdge edge = OpsGraphEdge.builder()
                .conditionType("route-match")
                .condition("logs")
                .build();
        OverAllState allowed = new OverAllState(Map.of(
                "selectedRoutes", List.of("es"),
                "intentAllowedRoutes", List.of("logs"),
                "intentExcludedRoutes", List.of()));
        OverAllState excluded = new OverAllState(Map.of(
                "selectedRoutes", List.of("es"),
                "intentAllowedRoutes", List.of("logs"),
                "intentExcludedRoutes", List.of("es")));

        assertTrue(evaluator.matches(edge, allowed, context));
        assertFalse(evaluator.matches(edge, excluded, context));
    }

    @Test
    void routeMatchFallsBackToOutputAndInvestigationPlan() {
        OpsGraphEdge edge = OpsGraphEdge.builder()
                .conditionType("route_match")
                .condition("rag")
                .build();
        OverAllState outputState = new OverAllState(Map.of("output", "needs:knowledge"));
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(List.of(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                .source("vector")
                                .build()))
                        .build();
        OverAllState planState = new OverAllState(Map.of("plan", plan));

        assertTrue(evaluator.matches(edge, outputState, context));
        assertTrue(evaluator.matches(edge, planState, context));
    }

    @Test
    void reviewDecisionUsesStructuredAndTextualRouteEvidence() {
        OpsGraphEdge edge = OpsGraphEdge.builder()
                .conditionType("review_decision")
                .condition("needs:prom")
                .build();
        OverAllState state = new OverAllState(Map.of(
                "review_decision", "continue with routeKey=metrics",
                "intentAllowedRoutes", List.of("prom")));

        assertTrue(evaluator.matches(edge, state, context));
    }

    @Test
    void expressionContainsAndPlanConditionsRemainCompatible() {
        assertTrue(evaluator.matches("contains:timeout",
                new OverAllState(Map.of("output", "database timeout")), context));
        assertTrue(evaluator.matches("runtime.round >= 2",
                new OverAllState(Map.of("round", 2)), context));
        assertTrue(evaluator.matches("evidence_sufficient || round_limit",
                new OverAllState(Map.of("evidence_sufficient", true)), context));
        assertFalse(evaluator.matches("evidence_sufficient || round_limit",
                new OverAllState(Map.of()), context));
        assertFalse(evaluator.matches("runtime.round >= 3",
                new OverAllState(Map.of("round", 2)), context));
        assertFalse(evaluator.matches("#state['round'] >= 2",
                new OverAllState(Map.of("round", 2)), context));
    }

    @Test
    void edgeClassificationDistinguishesAlwaysDefaultAndConditional() {
        assertTrue(evaluator.isAlwaysEdge(OpsGraphEdge.builder()
                .conditionType("always").condition("always").build()));
        assertTrue(evaluator.isDefaultEdge(OpsGraphEdge.builder()
                .conditionType("default").condition("final_report").build()));
        assertTrue(evaluator.isConditionalEdge(OpsGraphEdge.builder()
                .conditionType("contains").condition("failed").build()));
        assertFalse(evaluator.isConditionalEdge(OpsGraphEdge.builder()
                .conditionType("default").defaultEdge(true).build()));
    }
}
