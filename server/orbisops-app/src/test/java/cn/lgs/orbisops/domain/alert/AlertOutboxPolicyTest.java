package cn.lgs.orbisops.domain.alert;

import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxQuotaDecision;
import cn.lgs.orbisops.domain.alert.service.AlertOutboxPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertOutboxPolicyTest {

    private final AlertOutboxPolicy policy = new AlertOutboxPolicy();

    @Test
    void quotaAcceptsCapacityRejectsNormalAndPreemptsCritical() {
        assertEquals(AlertOutboxQuotaDecision.ACCEPT, policy.quota(9, 10, 20));
        assertEquals(AlertOutboxQuotaDecision.REJECT, policy.quota(10, 10, 99));
        assertEquals(AlertOutboxQuotaDecision.PREEMPT_LOWER_PRIORITY, policy.quota(10, 10, 100));
    }

    @Test
    void failurePlanOwnsExponentialBackoffAndDeadLetterBoundary() {
        AlertOutboxFailurePlan first = policy.failure(0, 3, "network");
        AlertOutboxFailurePlan third = policy.failure(2, 3, "network");
        AlertOutboxFailurePlan capped = policy.failure(20, 30, "network");

        assertEquals(1, first.nextRetryCount());
        assertEquals(60, first.retryDelaySeconds());
        assertFalse(first.deadLetter());
        assertEquals(3, third.nextRetryCount());
        assertTrue(third.deadLetter());
        assertEquals(3600, capped.retryDelaySeconds());
    }

    @Test
    void operationalBoundsAreCentralized() {
        assertEquals(1, policy.maxQueued(0));
        assertEquals(1, policy.maxRunning(0));
        assertEquals(1, policy.maxAttempts(0));
        assertEquals(30, policy.lockTimeoutSeconds(1));
        assertEquals(1, policy.batchLimit(0));
        assertEquals(100, policy.batchLimit(999));
    }
}
