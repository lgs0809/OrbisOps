package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.time.Instant;

public record DurableWorkflowRouteDecision(
        String edgeId,
        String fromNodeId,
        String toNodeId,
        int attempt,
        String ruleHash,
        Instant selectedAt
) {

    public DurableWorkflowRouteDecision {
        edgeId = required(edgeId, "DURABLE_WORKFLOW_ROUTE_ID_REQUIRED");
        fromNodeId = required(fromNodeId, "DURABLE_WORKFLOW_ROUTE_FROM_REQUIRED");
        toNodeId = required(toNodeId, "DURABLE_WORKFLOW_ROUTE_TO_REQUIRED");
        if (attempt < 0) throw new IllegalArgumentException("DURABLE_WORKFLOW_ROUTE_ATTEMPT_INVALID");
        ruleHash = required(ruleHash, "DURABLE_WORKFLOW_ROUTE_RULE_HASH_REQUIRED");
        if (selectedAt == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_ROUTE_TIME_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
