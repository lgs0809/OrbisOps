package cn.lgs.orbisops.application.channel.provider;

public record ChannelConversationRef(String externalConversationId,
                                     ConversationKind kind) {
    public ChannelConversationRef {
        externalConversationId = required(externalConversationId, "CHANNEL_CONVERSATION_ID_REQUIRED");
        kind = kind == null ? ConversationKind.UNKNOWN : kind;
    }

    public enum ConversationKind {
        DIRECT,
        GROUP,
        THREAD,
        UNKNOWN
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
