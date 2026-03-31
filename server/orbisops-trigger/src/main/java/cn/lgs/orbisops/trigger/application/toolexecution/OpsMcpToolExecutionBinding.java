package cn.lgs.orbisops.trigger.application.toolexecution;

/**
 * In-process compatibility binding for legacy MCP execution entries.
 * It carries only stable provider identities, never endpoint or credentials.
 */
public record OpsMcpToolExecutionBinding(
        String mcpId,
        String remoteToolName,
        Boolean readOnlyHint
) {

    public static final String CONTEXT_KEY = OpsMcpToolExecutionBinding.class.getName();

    public OpsMcpToolExecutionBinding {
        mcpId = required(mcpId, "MCP_COMPATIBILITY_BINDING_ID_REQUIRED");
        remoteToolName = required(
                remoteToolName,
                "MCP_COMPATIBILITY_REMOTE_TOOL_REQUIRED");
    }

    public OpsMcpToolExecutionBinding(String mcpId, String remoteToolName) {
        this(mcpId, remoteToolName, null);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
