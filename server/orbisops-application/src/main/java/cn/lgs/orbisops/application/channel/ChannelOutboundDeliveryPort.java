package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

import java.util.Map;

public interface ChannelOutboundDeliveryPort {

    ChannelDeliveryResult send(ChannelRecord channel,
                               Map<String, Object> configuration,
                               String target,
                               String content,
                               Map<String, Object> metadata);
}
