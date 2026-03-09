package cn.lgs.orbisops.trigger.ops.runtime;

/** Stable lookup identity for one runtime MCP configuration. */
public record OpsMcpRuntimeConfigRequest(
        String projectId,
        String mcpId
) {

    public OpsMcpRuntimeConfigRequest {
        projectId = text(projectId);
        mcpId = required(mcpId, "MCP_RUNTIME_CONFIG_ID_REQUIRED");
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
