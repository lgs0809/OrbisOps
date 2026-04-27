package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps durable Channel outbox records to stable administrative views. */
public final class OpsChannelOutboxViewMapper {

    private final OpsChannelOutboxMetadataCodec metadataCodec;

    public OpsChannelOutboxViewMapper() {
        this(new OpsChannelOutboxMetadataCodec());
    }

    public OpsChannelOutboxViewMapper(OpsChannelOutboxMetadataCodec metadataCodec) {
        if (metadataCodec == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_METADATA_CODEC_REQUIRED");
        this.metadataCodec = metadataCodec;
    }

    public Map<String, Object> toView(ChannelOutboxRecord record) {
        if (record == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_RECORD_REQUIRED");
        Map<String, Object> metadata = metadata(record.metadataJson());
        String messageType = text(metadata.get("messageType"), "ANALYSIS_NOTIFICATION");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.id());
        view.put("dedupKey", record.dedupKey());
        view.put("projectId", record.projectId());
        view.put("channelId", record.channelId());
        view.put("target", record.target());
        view.put("analysisId", record.analysisId());
        view.put("messageType", messageType);
        view.put("referenceId", "CHAT_REPLY".equals(messageType)
                ? text(metadata.get("replyToMessageId"), record.analysisId())
                : record.analysisId());
        view.put("status", record.status());
        view.put("retryCount", record.retryCount());
        view.put("lastError", record.lastError());
        view.put("lastResponse", record.lastResponse());
        view.put("nextRetryAt", record.nextRetryAt());
        view.put("leaseExpiresAt", record.leaseExpiresAt());
        view.put("deadLetterAt", record.deadLetterAt());
        view.put("lastAttemptAt", record.lastAttemptAt());
        view.put("createTime", record.createTime());
        view.put("updateTime", record.updateTime());
        return Collections.unmodifiableMap(new LinkedHashMap<>(view));
    }

    Map<String, Object> metadata(String metadataJson) {
        return metadataCodec.decode(metadataJson);
    }

    private static String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
