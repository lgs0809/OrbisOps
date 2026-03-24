package cn.lgs.orbisops.domain.agenteval.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentEvalNode(
        String nodeId,
        String type,
        Map<String, Object> configuration) {

    public AgentEvalNode {
        nodeId = text(nodeId);
        type = text(type);
        configuration = configuration == null || configuration.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
