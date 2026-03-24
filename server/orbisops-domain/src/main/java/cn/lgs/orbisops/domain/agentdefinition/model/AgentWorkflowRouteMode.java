package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;

/** Stable route language for workflow edges. */
public enum AgentWorkflowRouteMode {
    ALWAYS,
    DEFAULT,
    ROUTE_MATCH,
    REVIEW_DECISION,
    EXPRESSION,
    CONTAINS,
    ERROR;

    public static AgentWorkflowRouteMode fromPublishedName(
            String value,
            boolean defaultEdge) {
        if (defaultEdge) return DEFAULT;
        String normalized = value == null || value.trim().isBlank()
                ? "ALWAYS"
                : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("WORKFLOW_ROUTE_MODE_UNKNOWN:" + normalized);
        }
    }

    public boolean conditional() {
        return this != ALWAYS && this != DEFAULT;
    }
}
