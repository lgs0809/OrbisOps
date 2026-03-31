package cn.lgs.orbisops.domain.toolset.model;

/** Immutable provider facts. Credentials and live clients are intentionally excluded. */
public record ToolProviderDescriptor(
        ToolProviderType providerType,
        String providerId,
        String adapterType,
        String commandTemplate,
        String mcpServerId,
        String remoteToolName,
        String httpConfigJson,
        String dbConfigJson
) {

    public ToolProviderDescriptor {
        if (providerType == null) throw new IllegalArgumentException("TOOL_PROVIDER_TYPE_REQUIRED");
        providerId = text(providerId);
        adapterType = required(adapterType, "TOOL_ADAPTER_TYPE_REQUIRED");
        commandTemplate = text(commandTemplate);
        mcpServerId = text(mcpServerId);
        remoteToolName = text(remoteToolName);
        httpConfigJson = json(httpConfigJson);
        dbConfigJson = json(dbConfigJson);
    }

    public static ToolProviderDescriptor local(String adapterType) {
        return new ToolProviderDescriptor(
                ToolProviderType.LOCAL, adapterType, adapterType,
                "", "", "", "{}", "{}");
    }

    public static ToolProviderDescriptor mcp(String adapterType) {
        return new ToolProviderDescriptor(
                ToolProviderType.MCP, adapterType, adapterType,
                "", "", "", "{}", "{}");
    }

    public static ToolProviderDescriptor builtIn(String adapterType) {
        return new ToolProviderDescriptor(
                ToolProviderType.BUILT_IN, adapterType, adapterType,
                "", "", "", "{}", "{}");
    }

    public ToolProviderDescriptor withLegacyDetails(
            String commandTemplate,
            String mcpServerId,
            String remoteToolName,
            String httpConfigJson,
            String dbConfigJson) {
        return new ToolProviderDescriptor(
                providerType, providerId, adapterType,
                commandTemplate, mcpServerId, remoteToolName,
                httpConfigJson, dbConfigJson);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String json(String value) {
        String normalized = text(value);
        return normalized.isBlank() ? "{}" : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
