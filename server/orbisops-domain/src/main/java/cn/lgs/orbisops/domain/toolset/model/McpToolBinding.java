package cn.lgs.orbisops.domain.toolset.model;

public record McpToolBinding(
        String mcpServerId,
        String remoteToolName,
        String credentialReference
) implements ToolBinding {

    public McpToolBinding {
        mcpServerId = required(mcpServerId, "MCP_TOOL_SERVER_REQUIRED");
        remoteToolName = text(remoteToolName);
        credentialReference = text(credentialReference);
    }

    @Override
    public ToolProviderType providerType() {
        return ToolProviderType.MCP;
    }

    @Override
    public String providerId() {
        return mcpServerId;
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
