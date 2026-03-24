package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed edge projection with an explicit route mode and raw rule expression. */
public record AgentWorkflowEdgeDefinition(
        String edgeId,
        String name,
        String fromNodeId,
        String toNodeId,
        AgentWorkflowRouteMode routeMode,
        AgentWorkflowRuleExpression rule,
        boolean defaultEdge,
        boolean feedbackEdge,
        int priority,
        Map<String, Object> dataMapping,
        String description
) {

    public AgentWorkflowEdgeDefinition {
        edgeId = text(edgeId);
        name = text(name);
        fromNodeId = required(fromNodeId, "WORKFLOW_EDGE_FROM_REQUIRED");
        toNodeId = required(toNodeId, "WORKFLOW_EDGE_TO_REQUIRED");
        if (routeMode == null) throw new IllegalArgumentException("WORKFLOW_EDGE_ROUTE_MODE_REQUIRED");
        rule = rule == null ? new AgentWorkflowRuleExpression(routeMode, "") : rule;
        if (rule.mode() != routeMode) {
            throw new IllegalArgumentException("WORKFLOW_EDGE_RULE_MODE_MISMATCH");
        }
        if (defaultEdge && routeMode != AgentWorkflowRouteMode.DEFAULT) {
            throw new IllegalArgumentException("WORKFLOW_DEFAULT_EDGE_ROUTE_MODE_INVALID");
        }
        dataMapping = dataMapping == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dataMapping));
        description = text(description);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
