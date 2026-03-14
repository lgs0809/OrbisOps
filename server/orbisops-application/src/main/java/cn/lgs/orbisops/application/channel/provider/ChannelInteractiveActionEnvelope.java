package cn.lgs.orbisops.application.channel.provider;

import java.time.Instant;

public record ChannelInteractiveActionEnvelope(ChannelInteractiveAction action,
                                                ChannelExternalPrincipal actor,
                                                ChannelMessageRef message,
                                                Instant receivedAt,
                                                String idempotencyKey) {
    public ChannelInteractiveActionEnvelope {
        if (action == null) throw new IllegalArgumentException("CHANNEL_ACTION_REQUIRED");
        if (actor == null) throw new IllegalArgumentException("CHANNEL_EXTERNAL_PRINCIPAL_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REF_REQUIRED");
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        idempotencyKey = required(idempotencyKey, "CHANNEL_IDEMPOTENCY_KEY_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
