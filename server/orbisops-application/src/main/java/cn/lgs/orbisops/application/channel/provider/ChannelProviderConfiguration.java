package cn.lgs.orbisops.application.channel.provider;

public interface ChannelProviderConfiguration {
    ChannelType type();

    String channelId();

    String projectId();

    String credentialRef();

    ChannelConnectionMode connectionMode();
}
