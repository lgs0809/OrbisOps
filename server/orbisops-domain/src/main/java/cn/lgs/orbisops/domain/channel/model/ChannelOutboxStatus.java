package cn.lgs.orbisops.domain.channel.model;

public record ChannelOutboxStatus(int pending, int deadLetters) {
}
