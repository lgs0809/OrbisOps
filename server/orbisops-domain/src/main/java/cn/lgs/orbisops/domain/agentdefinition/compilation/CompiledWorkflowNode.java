package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowResourceReference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CompiledWorkflowNode(
        String nodeId,
        AgentWorkflowNodeType nodeType,
        String publishedType,
        String compilerId,
        List<AgentWorkflowResourceReference> resources,
        Map<String, Object> config
) {

    public CompiledWorkflowNode {
        nodeId = required(nodeId, "COMPILED_WORKFLOW_NODE_ID_REQUIRED");
        if (nodeType == null) throw new IllegalArgumentException("COMPILED_WORKFLOW_NODE_TYPE_REQUIRED");
        publishedType = required(publishedType, "COMPILED_WORKFLOW_PUBLISHED_TYPE_REQUIRED");
        compilerId = required(compilerId, "COMPILED_WORKFLOW_NODE_COMPILER_REQUIRED");
        resources = resources == null ? List.of() : List.copyOf(resources);
        config = config == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
