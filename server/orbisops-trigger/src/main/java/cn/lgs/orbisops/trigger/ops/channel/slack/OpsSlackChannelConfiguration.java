package cn.lgs.orbisops.trigger.ops.channel.slack;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

public record OpsSlackChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        String appCredentialRef,
        ChannelConnectionMode connectionMode,
        boolean requireMention) implements ChannelProviderConfiguration {

    public OpsSlackChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "SLACK_BOT_TOKEN_REF_REQUIRED");
        appCredentialRef = required(appCredentialRef, "SLACK_APP_CREDENTIAL_REF_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SLACK;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
