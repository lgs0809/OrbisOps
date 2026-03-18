package cn.lgs.orbisops.domain.source.model;

import java.util.List;
import java.util.Map;

public record SourceMcpRuntimeSpec(
        String name,
        String description,
        String transport,
        String command,
        List<String> args,
        Map<String, String> env,
        int timeoutSeconds,
        Map<String, String> toolCapabilities,
        List<String> allowedTools) {

    public SourceMcpRuntimeSpec {
        name = required(name, "SOURCE_MCP_NAME_REQUIRED");
        description = value(description);
        transport = required(transport, "SOURCE_MCP_TRANSPORT_REQUIRED");
        command = required(command, "SOURCE_MCP_COMMAND_REQUIRED");
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        timeoutSeconds = Math.max(1, timeoutSeconds);
        toolCapabilities = toolCapabilities == null ? Map.of() : Map.copyOf(toolCapabilities);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
