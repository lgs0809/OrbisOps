package cn.lgs.orbisops.domain.toolset.model;

/** Invocation protocol binding. It is deliberately separate from business effect and risk. */
public sealed interface ToolBinding
        permits InternalToolBinding, McpToolBinding {

    ToolProviderType providerType();

    String providerId();

    static ToolBinding compatibility(ToolProviderDescriptor provider) {
        if (provider == null) throw new IllegalArgumentException("TOOL_PROVIDER_REQUIRED");
        return switch (provider.providerType()) {
            case MCP -> new McpToolBinding(
                    fallback(provider.mcpServerId(), provider.providerId()),
                    provider.remoteToolName(),
                    "");
            case LOCAL, BUILT_IN, HTTP_API, SKILL, INTERNAL ->
                    new InternalToolBinding(provider.adapterType());
        };
    }

    private static String fallback(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? (fallback == null ? "" : fallback.trim()) : normalized;
    }
}
