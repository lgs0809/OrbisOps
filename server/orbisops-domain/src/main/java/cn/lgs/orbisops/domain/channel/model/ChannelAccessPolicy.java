package cn.lgs.orbisops.domain.channel.model;

import java.util.Locale;

/** Governs whether an external Channel principal may enter OrbisOps runtime before platform identity is resolved. */
public enum ChannelAccessPolicy {
    DENY_UNKNOWN,
    PAIRING,
    ALLOWLIST,
    OBSERVE_ONLY_UNKNOWN;

    public static ChannelAccessPolicy require(String value) {
        return parse(value, DENY_UNKNOWN);
    }

    public static ChannelAccessPolicy parse(String value, ChannelAccessPolicy fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return fallback == null ? DENY_UNKNOWN : fallback;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("CHANNEL_ACCESS_POLICY_INVALID:" + normalized, failure);
        }
    }

    public boolean allowsUnknownRuntime() {
        return this == OBSERVE_ONLY_UNKNOWN;
    }
}
