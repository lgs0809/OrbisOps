package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

/** Classifies Channel delivery failures into retry, dead-letter and operator-facing outcomes. */
public final class OpsChannelOutboxDeliveryFailurePolicy {

    private static final String OUTCOME_UNKNOWN_REASON = "CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND";

    private final ChannelOutboundContentPolicy outboundContentPolicy;

    public OpsChannelOutboxDeliveryFailurePolicy(ChannelOutboundContentPolicy outboundContentPolicy) {
        if (outboundContentPolicy == null) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_CONTENT_POLICY_REQUIRED");
        }
        this.outboundContentPolicy = outboundContentPolicy;
    }

    public Assessment assess(Throwable error, int retryCount, int maxAttempts) {
        int retry = Math.max(1, retryCount);
        int safeMaxAttempts = Math.max(1, Math.min(maxAttempts, 20));
        int delaySeconds = Math.min(3600, 30 * (1 << Math.min(retry, 6)));
        String safeError = abbreviate(outboundContentPolicy.sanitize(
                error == null ? null : error.getMessage()), 2000);
        boolean outcomeUnknown = containsReason(error, OUTCOME_UNKNOWN_REASON);
        boolean deadLetter = outcomeUnknown || retry >= safeMaxAttempts;
        String message = outcomeUnknown
                ? "Channel 已返回投递成功但本地审计失败，结果进入人工处理且不会自动重试。"
                : deadLetter
                        ? "Channel 通知达到最大重试次数，已进入死信：" + safeError
                        : "Channel 通知投递失败，已进入重试：" + safeError;
        return new Assessment(retry, delaySeconds, deadLetter, outcomeUnknown, safeError, message);
    }

    private static boolean containsReason(Throwable error, String reasonCode) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(reasonCode)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String abbreviate(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    public record Assessment(
            int retryCount,
            int delaySeconds,
            boolean deadLetter,
            boolean outcomeUnknown,
            String safeError,
            String message) {
    }
}
