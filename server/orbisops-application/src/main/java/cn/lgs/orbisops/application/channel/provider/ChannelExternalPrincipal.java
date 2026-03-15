package cn.lgs.orbisops.application.channel.provider;

public record ChannelExternalPrincipal(String externalPrincipalId,
                                       String displayName,
                                       PrincipalKind kind) {
    public ChannelExternalPrincipal {
        externalPrincipalId = required(externalPrincipalId, "CHANNEL_EXTERNAL_PRINCIPAL_REQUIRED");
        displayName = text(displayName);
        kind = kind == null ? PrincipalKind.USER : kind;
    }

    public enum PrincipalKind {
        USER,
        BOT,
        SERVICE
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
