package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChannelOutboxDeliveryFailurePolicyTest {

    private final OpsChannelOutboxDeliveryFailurePolicy policy =
            new OpsChannelOutboxDeliveryFailurePolicy(new ChannelOutboundContentPolicy());

    @Test
    void retryableFailureMustCalculateExponentialDelayAndSanitizeError() {
        OpsChannelOutboxDeliveryFailurePolicy.Assessment assessment = policy.assess(
                new IllegalStateException("token=secret"),
                2,
                8);

        assertEquals(2, assessment.retryCount());
        assertEquals(120, assessment.delaySeconds());
        assertFalse(assessment.deadLetter());
        assertFalse(assessment.outcomeUnknown());
        assertFalse(assessment.safeError().contains("secret"));
        assertTrue(assessment.message().contains("已进入重试"));
    }

    @Test
    void maxAttemptsMustProduceDeadLetter() {
        OpsChannelOutboxDeliveryFailurePolicy.Assessment assessment = policy.assess(
                new IllegalStateException("failed"),
                8,
                8);

        assertTrue(assessment.deadLetter());
        assertFalse(assessment.outcomeUnknown());
        assertTrue(assessment.message().contains("达到最大重试次数"));
        assertEquals(1920, policy.assess(new IllegalStateException("failed"), 20, 20).delaySeconds());
    }

    @Test
    void auditFailureAfterExternalSuccessMustNeverAutoRetry() {
        Throwable nested = new IllegalStateException(
                "wrapper",
                new IllegalStateException("CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND"));

        OpsChannelOutboxDeliveryFailurePolicy.Assessment assessment = policy.assess(nested, 1, 8);

        assertTrue(assessment.outcomeUnknown());
        assertTrue(assessment.deadLetter());
        assertTrue(assessment.message().contains("不会自动重试"));
    }
}
