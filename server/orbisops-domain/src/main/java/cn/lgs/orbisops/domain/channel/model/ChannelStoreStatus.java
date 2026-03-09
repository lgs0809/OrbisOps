package cn.lgs.orbisops.domain.channel.model;

public record ChannelStoreStatus(int activeChannels, int failedMessages) {

    public boolean ready() {
        return activeChannels > 0;
    }
}
