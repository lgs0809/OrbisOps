package cn.lgs.orbisops.domain.mcpexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpExecutionRequest(
        McpExecutionConfig config,
        String actor,
        String rawInput,
        Map<String, Object> input,
        boolean trustedLandingRuntime) {

    public McpExecutionRequest {
        if (config == null) throw new IllegalArgumentException("MCP_EXECUTION_CONFIG_REQUIRED");
        actor = value(actor).isBlank() ? "ops-agent" : value(actor);
        rawInput = rawInput == null ? "" : rawInput;
        input = input == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    public String requestedToolName() {
        Object primary = input.get("toolName");
        Object fallback = input.get("remoteToolName");
        String name = value(primary == null ? fallback : primary);
        if (name.isBlank() && config.allowedTools().size() == 1) {
            return config.allowedTools().get(0);
        }
        return name;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
