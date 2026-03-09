package cn.lgs.orbisops.application.channel;

public interface ChannelRecoveryDispatchPort {

    void dispatch(String channelId, String externalMessageId);
}
