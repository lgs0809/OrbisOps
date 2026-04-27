package cn.lgs.orbisops.trigger.ops.channel.discord;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

public record OpsDiscordChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        ChannelConnectionMode connectionMode,
        boolean requireMention,
        boolean messageContentIntent) implements ChannelProviderConfiguration {

    public OpsDiscordChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "DISCORD_BOT_TOKEN_REF_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
    }

    @Override
    public ChannelType type() {
        return ChannelType.DISCORD;
    }

    public int gatewayIntents() {
        int guildMessages = 1 << 9;
        int directMessages = 1 << 12;
        int messageContent = messageContentIntent ? 1 << 15 : 0;
        return guildMessages | directMessages | messageContent;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
