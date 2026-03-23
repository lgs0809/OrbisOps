package cn.lgs.orbisops.trigger.ops.channel.telegram;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

public record OpsTelegramChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        ChannelConnectionMode connectionMode,
        boolean requireMention,
        String botUsername) implements ChannelProviderConfiguration {

    public OpsTelegramChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "TELEGRAM_BOT_TOKEN_REF_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
        botUsername = normalizeUsername(botUsername);
    }

    @Override
    public ChannelType type() {
        return ChannelType.TELEGRAM;
    }

    private static String normalizeUsername(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.startsWith("@")) normalized = normalized.substring(1);
        return normalized;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
