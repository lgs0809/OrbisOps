package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

public interface ChannelInboundProtocolPort {

    VerifiedInbound verifyAndProtect(ChannelRecord channel,
                                     ChannelModels.InboundMessage message,
                                     String timestampHeader,
                                     String signature);

    ChannelModels.InboundMessage restore(ChannelMessageRecord stored);

    record VerifiedInbound(String storagePayloadJson) {
        public VerifiedInbound {
            storagePayloadJson = storagePayloadJson == null ? "" : storagePayloadJson.trim();
            if (storagePayloadJson.isBlank()) {
                throw new IllegalArgumentException("CHANNEL_INBOUND_STORAGE_PAYLOAD_REQUIRED");
            }
        }
    }
}
