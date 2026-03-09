package cn.lgs.orbisops.domain.channel.model;

import java.util.Locale;

public enum ChannelStatus {
    ACTIVE,
    DISABLED;

    public static ChannelStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return ACTIVE;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("CHANNEL_STATUS_UNKNOWN:" + normalized);
        }
    }
}
