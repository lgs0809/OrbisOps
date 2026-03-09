package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluator;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluates graph edge conditions through the allow-listed Rule AST only. */
final class OpsGraphConditionEvaluator {

    private final OpsWorkflowRuleCompilerAdapter ruleCompiler;
    private final WorkflowRuleEvaluator ruleEvaluator;
    private final OpsWorkflowRuleRuntimeContextFactory contextFactory;

    OpsGraphConditionEvaluator() {
        this(
                new OpsWorkflowRuleCompilerAdapter(),
                new WorkflowRuleEvaluator(),
                new OpsWorkflowRuleRuntimeContextFactory());
    }

    OpsGraphConditionEvaluator(
            OpsWorkflowRuleCompilerAdapter ruleCompiler,
            WorkflowRuleEvaluator ruleEvaluator,
            OpsWorkflowRuleRuntimeContextFactory contextFactory) {
        if (ruleCompiler == null) throw new IllegalArgumentException("WORKFLOW_RULE_COMPILER_REQUIRED");
        if (ruleEvaluator == null) throw new IllegalArgumentException("WORKFLOW_RULE_EVALUATOR_REQUIRED");
        if (contextFactory == null) throw new IllegalArgumentException("WORKFLOW_RULE_CONTEXT_FACTORY_REQUIRED");
        this.ruleCompiler = ruleCompiler;
        this.ruleEvaluator = ruleEvaluator;
        this.contextFactory = contextFactory;
    }

    boolean matches(OpsGraphEdge edge, OverAllState state) {
        if (edge == null) return false;
        String type = text(edge.getConditionType()).toLowerCase(Locale.ROOT);
        String condition = text(edge.getCondition());
        if ("default".equals(type)) return false;
        if ("always".equals(type)) return true;
        if ("route_match".equals(type)) return routeMatches(condition, state);
        if ("review_decision".equals(type)) return reviewMatches(condition, state);
        if ("contains".equals(type)) {
            return state.value("output", "")
                    .toLowerCase(Locale.ROOT)
                    .contains(condition.toLowerCase(Locale.ROOT));
        }
        if ("error".equals(type)) {
            return state.value("error").isPresent();
        }
        return matches(condition, state);
    }

    boolean matches(OpsGraphEdge edge, OverAllState state, Context context) {
        if (edge == null || context == null) return false;
        String type = normalizeConditionType(edge.getConditionType());
        if ("route_match".equals(type)) {
            return routeMatches(edge.getCondition(), state, context);
        }
        if ("review_decision".equals(type)) {
            return reviewDecisionMatches(edge.getCondition(), state, context);
        }
        return matches(edge, state);
    }

    boolean matches(String condition, OverAllState state) {
        String value = text(condition);
        if (value.isBlank() || "always".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value) || "never".equalsIgnoreCase(value)) return false;
        try {
            return ruleEvaluator.evaluate(
                    ruleCompiler.parseExpression(value),
                    contextFactory.create(state));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    boolean matches(String condition, OverAllState state, Context context) {
        return matches(condition, state);
    }

    boolean routeTextMatches(String expected, String value, Context context) {
        return routeTextContains(expected, value, context);
    }

    boolean routeTextContains(String expected, String value, Context context) {
        if (context == null || !StringUtils.hasText(expected) || !StringUtils.hasText(value)) return false;
        String normalizedExpected = context.normalizeAnalysisSource(expected);
        String normalizedText = value.toLowerCase(Locale.ROOT).replace("-", "_");
        if (normalizedText.contains("needs:" + normalizedExpected)
                || normalizedText.contains("\"routekey\":\"" + normalizedExpected + "\"")
                || normalizedText.contains("\"routekey\": \"" + normalizedExpected + "\"")
                || normalizedText.contains("routekey=" + normalizedExpected)) {
            return true;
        }
        for (String part : normalizedText.split("[^a-z0-9_]+")) {
            if (normalizedExpected.equals(context.normalizeAnalysisSource(part))) return true;
        }
        return false;
    }

    Set<String> stateRouteConstraint(OverAllState state, String key, Context context) {
        if (state == null || context == null) return Set.of();
        Object value = state.value(key).orElse(null);
        if (!(value instanceof Collection<?> values)) return Set.of();
        return values.stream()
                .map(String::valueOf)
                .map(context::normalizeAnalysisSource)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    boolean isConditionalEdge(OpsGraphEdge edge) {
        return isConditional(edge);
    }

    boolean isAlwaysEdge(OpsGraphEdge edge) {
        return edge != null
                && "always".equals(normalizeConditionType(edge.getConditionType()))
                && (!StringUtils.hasText(edge.getCondition())
                || "always".equalsIgnoreCase(edge.getCondition().trim()));
    }

    boolean isDefaultEdge(OpsGraphEdge edge) {
        return isDefault(edge);
    }

    String normalizeConditionType(String conditionType) {
        if (!StringUtils.hasText(conditionType)) return "always";
        return conditionType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    boolean isConditional(OpsGraphEdge edge) {
        if (edge == null || isDefault(edge)) return false;
        String type = text(edge.getConditionType()).toLowerCase(Locale.ROOT);
        String condition = text(edge.getCondition());
        if ("always".equals(type)) return false;
        return !condition.isBlank() && !"always".equalsIgnoreCase(condition);
    }

    boolean isDefault(OpsGraphEdge edge) {
        if (edge == null) return false;
        if (Boolean.TRUE.equals(edge.getDefaultEdge())) return true;
        String type = text(edge.getConditionType());
        String condition = text(edge.getCondition());
        return "default".equalsIgnoreCase(type)
                || "default".equalsIgnoreCase(condition)
                || "__default__".equalsIgnoreCase(condition);
    }

    boolean isNegative(OpsGraphEdge edge) {
        if (edge == null) return false;
        String type = text(edge.getConditionType()).toLowerCase(Locale.ROOT);
        String condition = text(edge.getCondition()).toLowerCase(Locale.ROOT);
        return "error".equals(type)
                || condition.contains("failed")
                || condition.contains("error")
                || condition.contains("abort")
                || condition.contains("fallback");
    }

    private boolean routeMatches(
            String condition,
            OverAllState state,
            Context context) {
        String expected = context.normalizeAnalysisSource(condition);
        if (!StringUtils.hasText(expected)) return false;
        Set<String> allowedByIntent = stateRouteConstraint(
                state, "intentAllowedRoutes", context);
        Set<String> excludedByIntent = stateRouteConstraint(
                state, "intentExcludedRoutes", context);
        if (excludedByIntent.contains(expected)
                || (!allowedByIntent.isEmpty() && !allowedByIntent.contains(expected))) {
            return false;
        }
        Optional<Object> selectedRoutes = state.value("selectedRoutes");
        if (selectedRoutes.isPresent() && selectedRoutes.get() instanceof Collection<?> routes) {
            return routes.stream()
                    .map(String::valueOf)
                    .map(context::normalizeAnalysisSource)
                    .anyMatch(expected::equals);
        }
        if (selectedRoutes.isPresent()) {
            return routeTextMatches(
                    expected, String.valueOf(selectedRoutes.get()), context);
        }
        if (routeTextMatches(
                expected, String.valueOf(state.value("output", "")), context)) {
            return true;
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                state.value("plan", OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.class)
                        .orElse(null);
        return context.hasSelectedAnalysisTask(plan, expected);
    }

    private boolean reviewDecisionMatches(
            String condition,
            OverAllState state,
            Context context) {
        String output = String.valueOf(state.value("output", ""));
        String reviewDecision = String.valueOf(state.value("review_decision", ""));
        String selectedReviewRoutes = String.valueOf(
                state.value("selectedReviewRoutes", ""));
        String expected = text(condition).toLowerCase(Locale.ROOT);
        String expectedSource = context.routeConditionSource(
                OpsGraphEdge.builder().condition(condition).build());
        if (context.isInvestigationRoute(expectedSource)) {
            Set<String> allowedByIntent = stateRouteConstraint(
                    state, "intentAllowedRoutes", context);
            Set<String> excludedByIntent = stateRouteConstraint(
                    state, "intentExcludedRoutes", context);
            if (excludedByIntent.contains(expectedSource)
                    || (!allowedByIntent.isEmpty()
                    && !allowedByIntent.contains(expectedSource))) {
                return false;
            }
        }
        return matches(condition, state)
                || output.toLowerCase(Locale.ROOT).contains(expected)
                || reviewDecision.toLowerCase(Locale.ROOT).contains(expected)
                || selectedReviewRoutes.toLowerCase(Locale.ROOT).contains(expected)
                || routeTextContains(expectedSource, output, context)
                || routeTextContains(expectedSource, reviewDecision, context)
                || routeTextContains(expectedSource, selectedReviewRoutes, context);
    }

    private boolean routeMatches(String condition, OverAllState state) {
        String expected = normalizeRoute(condition);
        if (expected.isBlank()) return false;
        Set<String> selectedRoutes = new LinkedHashSet<>();
        collectRoutes(state.value("selectedRoutes", ""), selectedRoutes);
        if (selectedRoutes.contains(expected)) return true;
        String output = state.value("output", "");
        if (!StringUtils.hasText(output)) return false;
        if ("multi".equalsIgnoreCase(output.trim())) {
            return selectedRoutes.contains(expected);
        }
        return normalizeRoute(output).equals(expected);
    }

    private boolean reviewMatches(String condition, OverAllState state) {
        String output = state.value("output", "");
        String expected = text(condition).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(output)
                && output.toLowerCase(Locale.ROOT).contains(expected)) {
            return true;
        }
        if (!expected.startsWith("needs:")) return false;
        String source = expected.substring("needs:".length()).trim();
        if (source.isBlank()) return false;
        Set<String> selectedReviewRoutes = new LinkedHashSet<>();
        collectRoutes(state.value("selectedReviewRoutes", ""), selectedReviewRoutes);
        return selectedReviewRoutes.contains(normalizeRoute(source));
    }

    private void collectRoutes(String raw, Set<String> target) {
        if (!StringUtils.hasText(raw)) return;
        for (String item : raw.split("[,;\\s]+")) {
            String normalized = normalizeRoute(item);
            if (StringUtils.hasText(normalized)) target.add(normalized);
        }
    }

    private String normalizeRoute(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("needs:")) {
            normalized = normalized.substring("needs:".length()).trim();
        }
        if (Set.of("prom", "prometheus_metrics", "metric", "metrics").contains(normalized)) {
            return "prometheus";
        }
        if (Set.of("elastic", "es", "logs", "log", "elk").contains(normalized)) {
            return "elasticsearch";
        }
        if (Set.of("knowledge", "knowledge_base").contains(normalized)) {
            return "rag";
        }
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    interface Context {
        String normalizeAnalysisSource(String source);

        String routeConditionSource(OpsGraphEdge edge);

        boolean isInvestigationRoute(String source);

        boolean hasSelectedAnalysisTask(
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                String source);
    }
}
