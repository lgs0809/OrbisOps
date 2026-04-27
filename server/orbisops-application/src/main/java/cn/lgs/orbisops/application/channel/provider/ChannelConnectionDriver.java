package cn.lgs.orbisops.application.channel.provider;

/** Persistent provider connections are owned by infrastructure/adapter drivers, never application services. */
public interface ChannelConnectionDriver<C extends ChannelProviderConfiguration> {

    ChannelType type();

    void start(C configuration);

    void stop(String channelId);

    ChannelHealthSnapshot health(String channelId);

    /** Release provider-owned transport resources when the application is shutting down. */
    default void shutdown() {
    }
}
