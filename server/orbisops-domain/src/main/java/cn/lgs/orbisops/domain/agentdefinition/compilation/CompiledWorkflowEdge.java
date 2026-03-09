package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRuleExpression;
import cn.lgs.orbisops.domain.agentdefinition.rule.AllRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CompiledWorkflowEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        AgentWorkflowRouteMode routeMode,
        AgentWorkflowRuleExpression rule,
        WorkflowRule compiledRule,
        boolean defaultRoute,
        boolean feedbackEdge,
        int priority,
        Map<String, Object> dataMapping
) {

    public CompiledWorkflowEdge(
            String edgeId,
            String sourceNodeId,
            String targetNodeId,
            AgentWorkflowRouteMode routeMode,
            AgentWorkflowRuleExpression rule,
            boolean defaultRoute,
            boolean feedbackEdge,
            int priority,
            Map<String, Object> dataMapping) {
        this(
                edgeId, sourceNodeId, targetNodeId, routeMode, rule,
                new AllRule(java.util.List.of()),
                defaultRoute, feedbackEdge, priority, dataMapping);
    }

    public CompiledWorkflowEdge {
        edgeId = edgeId == null ? "" : edgeId.trim();
        sourceNodeId = required(sourceNodeId, "COMPILED_WORKFLOW_EDGE_SOURCE_REQUIRED");
        targetNodeId = required(targetNodeId, "COMPILED_WORKFLOW_EDGE_TARGET_REQUIRED");
        if (routeMode == null) throw new IllegalArgumentException("COMPILED_WORKFLOW_EDGE_ROUTE_REQUIRED");
        if (rule == null) throw new IllegalArgumentException("COMPILED_WORKFLOW_EDGE_RULE_REQUIRED");
        if (compiledRule == null) {
            throw new IllegalArgumentException("COMPILED_WORKFLOW_EDGE_RULE_AST_REQUIRED");
        }
        dataMapping = dataMapping == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dataMapping));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
