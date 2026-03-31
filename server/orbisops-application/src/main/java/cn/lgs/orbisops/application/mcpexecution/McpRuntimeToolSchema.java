package cn.lgs.orbisops.application.mcpexecution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed runtime schema boundary used by execution policy and activation checks. */
public record McpRuntimeToolSchema(
        Map<String, Object> attributes,
        boolean requiresActivation) {

    public McpRuntimeToolSchema {
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public boolean missing() {
        return attributes.isEmpty();
    }
}
