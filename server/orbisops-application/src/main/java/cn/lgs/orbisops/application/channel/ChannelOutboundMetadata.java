package cn.lgs.orbisops.application.channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable outbound correlation facts extracted from open Channel metadata. */
public record ChannelOutboundMetadata(
        String runId,
        String sessionId,
        String source,
        String analysisId,
        String replyToMessageId,
        Long outboxId,
        String taskId,
        String ruleId,
        String deliveryId) {

    public ChannelOutboundMetadata {
        runId = text(runId);
        sessionId = text(sessionId);
        source = text(source);
        analysisId = text(analysisId);
        replyToMessageId = text(replyToMessageId);
        taskId = text(taskId);
        ruleId = text(ruleId);
        deliveryId = text(deliveryId);
    }

    public static ChannelOutboundMetadata from(Map<String, ?> source) {
        Map<String, ?> safe = source == null ? Map.of() : source;
        return new ChannelOutboundMetadata(
                text(safe.get("runId")),
                text(safe.get("sessionId")),
                text(safe.get("source")),
                text(safe.get("analysisId")),
                text(safe.get("replyToMessageId")),
                longValue(safe.get("outboxId")),
                text(safe.get("taskId")),
                text(safe.get("ruleId")),
                text(safe.get("deliveryId")));
    }

    public ChannelOutboundMetadata withDeliveryId(String value) {
        return new ChannelOutboundMetadata(
                runId, sessionId, source, analysisId, replyToMessageId,
                outboxId, taskId, ruleId, value);
    }

    public String resolveDeliveryId(String messageId) {
        if (!deliveryId.isBlank()) return deliveryId;
        return outboxId == null ? text(messageId) : "channel-outbox-" + outboxId;
    }

    public Map<String, Object> toProtocolMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        putText(result, "runId", runId);
        putText(result, "sessionId", sessionId);
        putText(result, "source", source);
        putText(result, "analysisId", analysisId);
        putText(result, "replyToMessageId", replyToMessageId);
        if (outboxId != null) result.put("outboxId", outboxId);
        putText(result, "taskId", taskId);
        putText(result, "ruleId", ruleId);
        putText(result, "deliveryId", deliveryId);
        return Collections.unmodifiableMap(result);
    }

    private static void putText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        String normalized = text(value);
        if (normalized.isBlank()) return null;
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
