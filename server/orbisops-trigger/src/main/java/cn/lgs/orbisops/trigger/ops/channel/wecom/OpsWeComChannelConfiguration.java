package cn.lgs.orbisops.trigger.ops.channel.wecom;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

public record OpsWeComChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        String botId,
        ChannelConnectionMode connectionMode) implements ChannelProviderConfiguration {

    public OpsWeComChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "WECOM_BOT_SECRET_REF_REQUIRED");
        botId = required(botId, "WECOM_BOT_ID_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WECOM;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
