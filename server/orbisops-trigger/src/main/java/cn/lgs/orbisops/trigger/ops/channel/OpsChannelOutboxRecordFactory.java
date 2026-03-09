package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Creates stable durable outbox records for analysis notifications and chat replies. */
public final class OpsChannelOutboxRecordFactory {

    private final ChannelOutboundContentPolicy outboundContentPolicy;
    private final OpsChannelNotificationSettings settings;
    private final OpsChannelOutboxMetadataCodec metadataCodec;

    public OpsChannelOutboxRecordFactory(
            ChannelOutboundContentPolicy outboundContentPolicy,
            OpsChannelNotificationSettings settings) {
        this(outboundContentPolicy, settings, new OpsChannelOutboxMetadataCodec());
    }

    public OpsChannelOutboxRecordFactory(
            ChannelOutboundContentPolicy outboundContentPolicy,
            OpsChannelNotificationSettings settings,
            OpsChannelOutboxMetadataCodec metadataCodec) {
        if (outboundContentPolicy == null) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_CONTENT_POLICY_REQUIRED");
        }
        if (settings == null) {
            throw new IllegalArgumentException("CHANNEL_NOTIFICATION_SETTINGS_REQUIRED");
        }
        if (metadataCodec == null) {
            throw new IllegalArgumentException("CHANNEL_OUTBOX_METADATA_CODEC_REQUIRED");
        }
        this.outboundContentPolicy = outboundContentPolicy;
        this.settings = settings;
        this.metadataCodec = metadataCodec;
    }

    public ChannelOutboxRecord analysisNotification(AnalysisNotificationDraft draft) {
        if (draft == null) throw new IllegalArgumentException("CHANNEL_ANALYSIS_NOTIFICATION_DRAFT_REQUIRED");
        String projectId = require(draft.projectId(), "Channel 通知必须绑定 projectId");
        String channelId = require(draft.channelId(), "Channel 通知必须选择 channelId");
        String target = require(draft.target(), "Channel 通知必须填写目标会话或接收人");
        String analysisId = text(draft.analysisId(), "unknown");
        String dedupKey = "channel-notify:" + OpsChannelSignature.contentHash(
                projectId + "\n" + analysisId + "\n" + channelId + "\n" + target);
        String message = outboundContentPolicy.sanitize(analysisMessage(draft));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("runId", text(draft.runId()));
        metadata.put("analysisId", analysisId);
        return record(dedupKey, projectId, channelId, target, analysisId, message, metadata);
    }

    public ChannelOutboxRecord chatReply(ChatReplyDraft draft) {
        if (draft == null) throw new IllegalArgumentException("CHANNEL_CHAT_REPLY_DRAFT_REQUIRED");
        String projectId = require(draft.projectId(), "Channel 回复必须绑定 projectId");
        String channelId = require(draft.channelId(), "Channel 回复必须绑定 channelId");
        String target = require(draft.target(), "Channel 回复必须绑定目标会话");
        String replyToMessageId = require(draft.replyToMessageId(), "Channel 回复必须绑定来源消息");
        String message = outboundContentPolicy.sanitize(require(draft.content(), "Channel 回复内容不能为空"));
        String dedupKey = "channel-reply:" + OpsChannelSignature.contentHash(
                projectId + "\n" + channelId + "\n" + replyToMessageId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("messageType", "CHAT_REPLY");
        metadata.put("runId", text(draft.runId()));
        metadata.put("sessionId", text(draft.sessionId()));
        metadata.put("replyToMessageId", replyToMessageId);
        return record(dedupKey, projectId, channelId, target, replyToMessageId, message, metadata);
    }

    private ChannelOutboxRecord record(
            String dedupKey,
            String projectId,
            String channelId,
            String target,
            String referenceId,
            String message,
            Map<String, Object> metadata) {
        return new ChannelOutboxRecord(
                null,
                dedupKey,
                projectId,
                channelId,
                target,
                referenceId,
                "PENDING",
                message,
                metadataCodec.encode(metadata),
                OpsChannelSignature.contentHash(message),
                0,
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private String analysisMessage(AnalysisNotificationDraft draft) {
        StringBuilder content = new StringBuilder("# 运维 Agent 分析结果\n\n");
        if (draft.analysisId() != null || draft.generatedAt() != null || draft.markdownReport() != null) {
            content.append("- 分析 ID：").append(text(draft.analysisId(), "-")).append('\n');
            content.append("- 生成时间：").append(text(draft.generatedAt(), "-")).append("\n\n");
            content.append(hasText(draft.markdownReport()) ? draft.markdownReport() : "本次分析未生成报告。");
        }
        return abbreviate(content.toString(), settings.maxMessageChars());
    }

    private static String require(Object value, String message) {
        String text = text(value);
        if (!hasText(text)) throw new IllegalArgumentException(message);
        return text;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String text(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private static String abbreviate(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AnalysisNotificationDraft(
            String projectId,
            String channelId,
            String target,
            String runId,
            String analysisId,
            String generatedAt,
            String markdownReport) {
    }

    public record ChatReplyDraft(
            String projectId,
            String channelId,
            String target,
            String content,
            String runId,
            String sessionId,
            String replyToMessageId) {
    }
}
