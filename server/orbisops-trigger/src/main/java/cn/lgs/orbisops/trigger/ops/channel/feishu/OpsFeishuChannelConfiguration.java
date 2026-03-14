package cn.lgs.orbisops.trigger.ops.channel.feishu;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

public record OpsFeishuChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        String appId,
        ChannelConnectionMode connectionMode,
        boolean requireMention,
        boolean respondToMentionAll,
        String verificationTokenRef,
        String encryptKeyRef) implements ChannelProviderConfiguration {

    public OpsFeishuChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "FEISHU_APP_SECRET_REF_REQUIRED");
        appId = required(appId, "FEISHU_APP_ID_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
        verificationTokenRef = text(verificationTokenRef);
        encryptKeyRef = text(encryptKeyRef);
    }

    @Override
    public ChannelType type() {
        return ChannelType.FEISHU;
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
