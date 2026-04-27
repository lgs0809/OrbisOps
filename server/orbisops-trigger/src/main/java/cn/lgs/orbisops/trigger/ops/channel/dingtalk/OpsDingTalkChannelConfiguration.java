package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

public record OpsDingTalkChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        String clientId,
        String corpId,
        String robotCode,
        String cardTemplateId,
        ChannelConnectionMode connectionMode) implements ChannelProviderConfiguration {

    public OpsDingTalkChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "DINGTALK_CLIENT_SECRET_REF_REQUIRED");
        clientId = required(clientId, "DINGTALK_CLIENT_ID_REQUIRED");
        corpId = required(corpId, "DINGTALK_CORP_ID_REQUIRED");
        robotCode = required(robotCode, "DINGTALK_ROBOT_CODE_REQUIRED");
        cardTemplateId = required(cardTemplateId, "DINGTALK_CARD_TEMPLATE_ID_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.LONG_CONNECTION : connectionMode;
    }

    @Override
    public ChannelType type() {
        return ChannelType.DINGTALK;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
