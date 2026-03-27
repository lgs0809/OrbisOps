package cn.lgs.orbisops.domain.runtime.workflow.model;

public record BoundWorkflowRoute(
        String edgeId,
        String fromNodeId,
        String toNodeId,
        String routeMode,
        String ruleHash,
        String dataMappingHash,
        int priority,
        boolean defaultEdge,
        boolean feedbackEdge
) {

    public BoundWorkflowRoute {
        edgeId = required(edgeId, "BOUND_WORKFLOW_ROUTE_ID_REQUIRED");
        fromNodeId = required(fromNodeId, "BOUND_WORKFLOW_ROUTE_FROM_REQUIRED");
        toNodeId = required(toNodeId, "BOUND_WORKFLOW_ROUTE_TO_REQUIRED");
        routeMode = required(routeMode, "BOUND_WORKFLOW_ROUTE_MODE_REQUIRED");
        ruleHash = required(ruleHash, "BOUND_WORKFLOW_ROUTE_RULE_HASH_REQUIRED");
        dataMappingHash = required(dataMappingHash, "BOUND_WORKFLOW_ROUTE_MAPPING_HASH_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
