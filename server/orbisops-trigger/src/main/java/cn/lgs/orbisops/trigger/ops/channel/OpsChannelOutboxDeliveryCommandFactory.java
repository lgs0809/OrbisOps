package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Projects a durable outbox record into the Channel outbound command contract. */
public final class OpsChannelOutboxDeliveryCommandFactory {

    private final OpsChannelOutboxMetadataCodec metadataCodec;

    public OpsChannelOutboxDeliveryCommandFactory(OpsChannelOutboxMetadataCodec metadataCodec) {
        if (metadataCodec == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_METADATA_CODEC_REQUIRED");
        this.metadataCodec = metadataCodec;
    }

    public ChannelModels.Send create(ChannelOutboxRecord record) {
        if (record == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_RECORD_REQUIRED");
        Map<String, Object> metadata = new LinkedHashMap<>(metadataCodec.decode(record.metadataJson()));
        metadata.put("outboxId", record.id());
        return new ChannelModels.Send(
                record.projectId(),
                record.channelId(),
                record.target(),
                require(record.messageText(), "CHANNEL_NOTIFICATION_MESSAGE_MISSING"),
                Collections.unmodifiableMap(new LinkedHashMap<>(metadata)),
                "channel-notification-outbox");
    }

    private static String require(Object value, String message) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }
}
