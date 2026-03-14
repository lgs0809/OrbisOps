package cn.lgs.orbisops.application.channel.provider;

import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

import java.util.Optional;

/**
 * Typed provider SPI. Provider-specific configuration JSON is decoded inside
 * the adapter boundary by {@link #configuration(ChannelRecord)}.
 */
public interface ChannelProviderAdapter<C extends ChannelProviderConfiguration> {

    ChannelType type();

    ChannelCapabilitySet capabilities();

    C configuration(ChannelRecord channel);

    void validateConfiguration(C configuration);

    ChannelHealthSnapshot preflight(C configuration);

    ChannelInboundEnvelope parseInbound(C configuration, ChannelInboundPacket packet);

    ChannelDeliveryReceipt send(C configuration, ChannelOutboundMessage message);

    default ChannelDeliveryReceipt updateMessage(C configuration,
                                                 ChannelMessageRef existingMessage,
                                                 ChannelOutboundMessage message) {
        throw new UnsupportedOperationException("CHANNEL_MESSAGE_UPDATE_UNSUPPORTED:" + type());
    }

    default Optional<ChannelInteractiveActionEnvelope> parseInteractiveAction(C configuration,
                                                                               ChannelInboundPacket packet) {
        return Optional.empty();
    }
}
