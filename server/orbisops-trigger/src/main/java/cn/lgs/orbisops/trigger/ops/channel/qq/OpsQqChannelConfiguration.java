package cn.lgs.orbisops.trigger.ops.channel.qq;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

/** QQ Open Platform configuration. App ID is public metadata; App Secret stays behind credentialRef. */
public record OpsQqChannelConfiguration(
        String channelId,
        String projectId,
        String appId,
        String credentialRef,
        ChannelConnectionMode connectionMode) implements ChannelProviderConfiguration {

    private static final int GROUP_AND_C2C_INTENT = 1 << 25;
    private static final int INTERACTION_INTENT = 1 << 26;

    public OpsQqChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        appId = required(appId, "QQ_APP_ID_REQUIRED");
        credentialRef = required(credentialRef, "QQ_APP_SECRET_REF_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
    }

    @Override
    public ChannelType type() {
        return ChannelType.QQ;
    }

    public int gatewayIntents() {
        return GROUP_AND_C2C_INTENT | INTERACTION_INTENT;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
