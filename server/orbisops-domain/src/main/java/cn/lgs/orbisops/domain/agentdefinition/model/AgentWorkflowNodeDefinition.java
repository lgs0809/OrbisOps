package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed node projection of the authoritative AgentGraphDefinition. */
public record AgentWorkflowNodeDefinition(
        String nodeId,
        AgentWorkflowNodeType nodeType,
        String publishedType,
        String mode,
        String agent,
        String description,
        String instruction,
        List<AgentWorkflowResourceReference> resources,
        Map<String, Object> config
) {

    public AgentWorkflowNodeDefinition {
        nodeId = required(nodeId, "WORKFLOW_NODE_ID_REQUIRED");
        if (nodeType == null) throw new IllegalArgumentException("WORKFLOW_NODE_TYPE_REQUIRED");
        publishedType = AgentWorkflowNodeType.normalizePublishedName(publishedType);
        AgentWorkflowNodeType parsed = AgentWorkflowNodeType.fromPublishedName(publishedType);
        if (parsed != nodeType) {
            throw new IllegalArgumentException(
                    "WORKFLOW_NODE_TYPE_MISMATCH:" + publishedType + ":" + nodeType);
        }
        mode = text(mode);
        agent = text(agent);
        description = text(description);
        instruction = text(instruction);
        resources = resources == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(resources));
        config = config == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }

    public boolean references(AgentWorkflowResourceReference.ResourceType type) {
        return type != null && resources.stream().anyMatch(reference ->
                reference != null && reference.resourceType() == type);
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
