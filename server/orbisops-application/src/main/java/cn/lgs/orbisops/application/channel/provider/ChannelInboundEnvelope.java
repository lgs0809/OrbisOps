package cn.lgs.orbisops.application.channel.provider;

import java.time.Instant;
import java.util.List;

public record ChannelInboundEnvelope(ChannelMessageRef message,
                                     ChannelExternalPrincipal sender,
                                     ChannelRichContent content,
                                     List<ChannelAttachment> attachments,
                                     Instant receivedAt,
                                     String idempotencyKey) {
    public ChannelInboundEnvelope {
        if (message == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REF_REQUIRED");
        if (sender == null) throw new IllegalArgumentException("CHANNEL_EXTERNAL_PRINCIPAL_REQUIRED");
        if (content == null) throw new IllegalArgumentException("CHANNEL_CONTENT_REQUIRED");
        attachments = attachments == null || attachments.isEmpty() ? List.of() : List.copyOf(attachments);
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        idempotencyKey = required(idempotencyKey, "CHANNEL_IDEMPOTENCY_KEY_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
