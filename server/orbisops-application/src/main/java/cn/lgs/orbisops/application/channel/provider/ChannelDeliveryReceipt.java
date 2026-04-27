package cn.lgs.orbisops.application.channel.provider;

import java.time.Instant;

public record ChannelDeliveryReceipt(boolean delivered,
                                     String status,
                                     String providerMessageId,
                                     Integer providerStatusCode,
                                     Instant observedAt) {
    public ChannelDeliveryReceipt {
        status = required(status, "CHANNEL_DELIVERY_STATUS_REQUIRED");
        providerMessageId = text(providerMessageId);
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }

    public static ChannelDeliveryReceipt skipped(String status) {
        return new ChannelDeliveryReceipt(false, status, "", null, Instant.now());
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
