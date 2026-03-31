package cn.lgs.orbisops.domain.toolset.model;

public record InternalToolBinding(String handlerId) implements ToolBinding {

    public InternalToolBinding {
        handlerId = required(handlerId, "INTERNAL_TOOL_HANDLER_REQUIRED");
    }

    @Override
    public ToolProviderType providerType() {
        return ToolProviderType.INTERNAL;
    }

    @Override
    public String providerId() {
        return handlerId;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
