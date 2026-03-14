package cn.lgs.orbisops.application.channel.provider;

import java.util.Locale;

public enum ChannelType {
    GENERIC_WEBHOOK,
    FEISHU,
    WECOM,
    DINGTALK,
    SLACK,
    TELEGRAM,
    DISCORD,
    QQ,
    WECHAT;

    public static ChannelType parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("CHANNEL_TYPE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("CHANNEL_TYPE_UNSUPPORTED:" + normalized);
        }
    }
}
