package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

public interface ChannelOutboundDeliveryPort {

    ChannelDeliveryReceipt send(ChannelRecord channel, ChannelOutboundMessage message);

    default boolean supportsMessageUpdate(ChannelRecord channel) {
        return false;
    }

    default ChannelDeliveryReceipt update(ChannelRecord channel,
                                          ChannelMessageRef existingMessage,
                                          ChannelOutboundMessage message) {
        throw new UnsupportedOperationException("CHANNEL_MESSAGE_UPDATE_UNSUPPORTED");
    }
}
