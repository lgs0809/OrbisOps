package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record BoundWorkflowExecutionPlan(
        int schemaVersion,
        int definitionVersion,
        String definitionHash,
        String agentId,
        String sessionId,
        String runId,
        String projectId,
        String contextBundleId,
        String contextBundleHash,
        String startNodeId,
        List<BoundWorkflowNode> nodes,
        List<BoundWorkflowRoute> routes,
        List<BoundWorkflowResourceSnapshot> sharedResources,
        String planHash,
        Instant boundAt,
        List<String> completedBindingStages
) {

    public BoundWorkflowExecutionPlan {
        if (schemaVersion <= 0) throw new IllegalArgumentException("BOUND_WORKFLOW_SCHEMA_VERSION_INVALID");
        if (definitionVersion <= 0) throw new IllegalArgumentException("BOUND_WORKFLOW_DEFINITION_VERSION_INVALID");
        definitionHash = required(definitionHash, "BOUND_WORKFLOW_DEFINITION_HASH_REQUIRED");
        agentId = required(agentId, "BOUND_WORKFLOW_AGENT_ID_REQUIRED");
        sessionId = required(sessionId, "BOUND_WORKFLOW_SESSION_ID_REQUIRED");
        runId = required(runId, "BOUND_WORKFLOW_RUN_ID_REQUIRED");
        projectId = text(projectId);
        contextBundleId = required(contextBundleId, "BOUND_WORKFLOW_CONTEXT_BUNDLE_ID_REQUIRED");
        contextBundleHash = required(contextBundleHash, "BOUND_WORKFLOW_CONTEXT_BUNDLE_HASH_REQUIRED");
        startNodeId = required(startNodeId, "BOUND_WORKFLOW_START_NODE_REQUIRED");
        nodes = nodes == null ? List.of() : nodes.stream()
                .sorted(Comparator.comparing(BoundWorkflowNode::nodeId))
                .toList();
        routes = routes == null ? List.of() : routes.stream()
                .sorted(Comparator.comparingInt(BoundWorkflowRoute::priority).reversed()
                        .thenComparing(BoundWorkflowRoute::edgeId))
                .toList();
        sharedResources = sharedResources == null ? List.of() : sharedResources.stream()
                .sorted(Comparator.comparing(BoundWorkflowResourceSnapshot::identityKey))
                .toList();
        planHash = required(planHash, "BOUND_WORKFLOW_PLAN_HASH_REQUIRED");
        if (boundAt == null) throw new IllegalArgumentException("BOUND_WORKFLOW_BOUND_AT_REQUIRED");
        completedBindingStages = completedBindingStages == null ? List.of() : List.copyOf(completedBindingStages);
        if (nodes.isEmpty()) throw new IllegalArgumentException("BOUND_WORKFLOW_NODES_REQUIRED");
        String effectiveStartNodeId = startNodeId;
        if (nodes.stream().noneMatch(node -> node.nodeId().equals(effectiveStartNodeId))) {
            throw new IllegalArgumentException("BOUND_WORKFLOW_START_NODE_UNKNOWN:" + effectiveStartNodeId);
        }
    }

    public List<BoundWorkflowResourceSnapshot> allResources() {
        return java.util.stream.Stream.concat(
                        sharedResources.stream(),
                        nodes.stream().flatMap(node -> node.resources().stream()))
                .sorted(Comparator.comparing(BoundWorkflowResourceSnapshot::identityKey))
                .toList();
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
