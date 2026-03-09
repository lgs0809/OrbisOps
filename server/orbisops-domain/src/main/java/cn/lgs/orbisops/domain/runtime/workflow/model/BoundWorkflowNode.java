package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.util.Comparator;
import java.util.List;

public record BoundWorkflowNode(
        String nodeId,
        String nodeType,
        String publishedType,
        String compilerId,
        String configHash,
        List<BoundWorkflowResourceSnapshot> resources
) {

    public BoundWorkflowNode {
        nodeId = required(nodeId, "BOUND_WORKFLOW_NODE_ID_REQUIRED");
        nodeType = required(nodeType, "BOUND_WORKFLOW_NODE_TYPE_REQUIRED");
        publishedType = required(publishedType, "BOUND_WORKFLOW_NODE_PUBLISHED_TYPE_REQUIRED");
        compilerId = required(compilerId, "BOUND_WORKFLOW_NODE_COMPILER_REQUIRED");
        configHash = required(configHash, "BOUND_WORKFLOW_NODE_CONFIG_HASH_REQUIRED");
        resources = resources == null ? List.of() : resources.stream()
                .sorted(Comparator.comparing(BoundWorkflowResourceSnapshot::identityKey))
                .toList();
        long identities = resources.stream().map(BoundWorkflowResourceSnapshot::identityKey).distinct().count();
        if (identities != resources.size()) {
            throw new IllegalArgumentException("BOUND_WORKFLOW_NODE_RESOURCE_DUPLICATE:" + nodeId);
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
