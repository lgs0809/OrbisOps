package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowNoMatchPolicy;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRouteSelectionMode;

import java.util.List;
import java.util.Locale;

/** Typed runtime route configuration and no-match policy projection. */
final class OpsGraphRouteSelectionPolicy {

    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphConditionEvaluator conditionEvaluator;
    private final OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy;

    OpsGraphRouteSelectionPolicy(
            OpsAnalysisRoutingPolicy routingPolicy,
            OpsGraphConditionEvaluator conditionEvaluator,
            OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy) {
        if (routingPolicy == null) throw new IllegalArgumentException("GRAPH_ROUTING_POLICY_REQUIRED");
        if (conditionEvaluator == null) throw new IllegalArgumentException("GRAPH_CONDITION_EVALUATOR_REQUIRED");
        if (nodeExecutionPolicy == null) throw new IllegalArgumentException("GRAPH_NODE_EXECUTION_POLICY_REQUIRED");
        this.routingPolicy = routingPolicy;
        this.conditionEvaluator = conditionEvaluator;
        this.nodeExecutionPolicy = nodeExecutionPolicy;
    }

    WorkflowRouteSelectionMode routeMode(
            OpsWorkflowNode node,
            boolean parallelSelection) {
        String configured = value(configText(node, "routeMode"))
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        return switch (configured) {
            case "SINGLE", "FIRST_MATCH" -> WorkflowRouteSelectionMode.FIRST_MATCH;
            case "MULTI", "FAN_OUT" -> WorkflowRouteSelectionMode.FAN_OUT;
            case "ALL_MATCH" -> WorkflowRouteSelectionMode.ALL_MATCH;
            case "DEFAULT_ONLY" -> WorkflowRouteSelectionMode.DEFAULT_ONLY;
            case "" -> parallelSelection
                    ? WorkflowRouteSelectionMode.FAN_OUT
                    : WorkflowRouteSelectionMode.FIRST_MATCH;
            default -> throw new IllegalArgumentException(
                    "WORKFLOW_ROUTE_SELECTION_MODE_UNSUPPORTED:" + configured);
        };
    }

    List<String> noMatchRoutes(OpsWorkflowNode node) {
        return switch (noMatchPolicy(node)) {
            case DEFAULT -> List.of("__default__");
            case END -> List.of("__end__");
            case FAIL -> throw new IllegalStateException("WORKFLOW_ROUTE_NO_MATCH");
        };
    }

    boolean allowsRoutingAfterGraphCompletion(OpsWorkflowNode node) {
        if (node == null) return false;
        String type = nodeExecutionPolicy.executionNodeType(node);
        return "REPORT".equals(type) || "NOTIFY".equals(type);
    }

    boolean useParallelConditionalEdges(OpsWorkflowNode node) {
        String configured = value(configText(node, "routeMode")).trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return !("ROUTER".equals(normalizeType(node == null ? null : node.getType()))
                && java.util.Set.of("SINGLE", "FIRST_MATCH", "DEFAULT_ONLY").contains(configured));
    }

    OpsGraphConditionEvaluator.Context conditionContext() {
        return new OpsGraphConditionEvaluator.Context() {
            @Override
            public String normalizeAnalysisSource(String source) {
                return routingPolicy.normalizeSource(source);
            }

            @Override
            public String routeConditionSource(OpsGraphEdge edge) {
                return routingPolicy.routeConditionSource(edge);
            }

            @Override
            public boolean isInvestigationRoute(String source) {
                return routingPolicy.isInvestigationRoute(source);
            }

            @Override
            public boolean hasSelectedAnalysisTask(
                    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                    String source) {
                return routingPolicy.selectedTask(plan, source).isPresent();
            }
        };
    }

    private WorkflowNoMatchPolicy noMatchPolicy(OpsWorkflowNode node) {
        String configured = value(configText(node, "noMatchBehavior"))
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        if (configured.isBlank()) return WorkflowNoMatchPolicy.DEFAULT;
        try {
            return WorkflowNoMatchPolicy.valueOf(configured);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "WORKFLOW_NO_MATCH_POLICY_UNSUPPORTED:" + configured);
        }
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank()
                ? "CHAT"
                : type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null || !node.getConfig().containsKey(key)) {
            return null;
        }
        Object configured = node.getConfig().get(key);
        return configured == null ? null : String.valueOf(configured);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
