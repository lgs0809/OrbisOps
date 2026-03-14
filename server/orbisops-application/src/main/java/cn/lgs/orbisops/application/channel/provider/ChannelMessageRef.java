package cn.lgs.orbisops.application.channel.provider;

public record ChannelMessageRef(String externalMessageId,
                                ChannelConversationRef conversation,
                                String threadId) {
    public ChannelMessageRef {
        externalMessageId = required(externalMessageId, "CHANNEL_MESSAGE_ID_REQUIRED");
        if (conversation == null) throw new IllegalArgumentException("CHANNEL_CONVERSATION_REQUIRED");
        threadId = threadId == null ? "" : threadId.trim();
    }

    public ChannelMessageRef(String externalMessageId, ChannelConversationRef conversation) {
        this(externalMessageId, conversation, "");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
