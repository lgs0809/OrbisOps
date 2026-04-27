package cn.lgs.orbisops.trigger.ops.channel.wechat;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;

/** WeChat Official Account secure-callback configuration. All secret material stays behind credential refs. */
public record OpsWeChatChannelConfiguration(
        String channelId,
        String projectId,
        String credentialRef,
        String appId,
        String verificationTokenRef,
        String encodingAesKeyRef,
        ChannelConnectionMode connectionMode) implements ChannelProviderConfiguration {

    public OpsWeChatChannelConfiguration {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        credentialRef = required(credentialRef, "WECHAT_APP_SECRET_REF_REQUIRED");
        appId = required(appId, "WECHAT_APP_ID_REQUIRED");
        verificationTokenRef = required(verificationTokenRef, "WECHAT_VERIFICATION_TOKEN_REF_REQUIRED");
        encodingAesKeyRef = required(encodingAesKeyRef, "WECHAT_ENCODING_AES_KEY_REF_REQUIRED");
        connectionMode = connectionMode == null ? ChannelConnectionMode.WEBHOOK : connectionMode;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WECHAT;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
