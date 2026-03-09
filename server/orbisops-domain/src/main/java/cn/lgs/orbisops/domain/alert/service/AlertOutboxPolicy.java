package cn.lgs.orbisops.domain.alert.service;

import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxQuotaDecision;

public final class AlertOutboxPolicy {

    private static final int CRITICAL_PRIORITY = 100;

    public AlertOutboxQuotaDecision quota(long activeCount, int configuredLimit, int priority) {
        int limit = maxQueued(configuredLimit);
        if (Math.max(0L, activeCount) < limit) return AlertOutboxQuotaDecision.ACCEPT;
        return Math.max(0, priority) >= CRITICAL_PRIORITY
                ? AlertOutboxQuotaDecision.PREEMPT_LOWER_PRIORITY
                : AlertOutboxQuotaDecision.REJECT;
    }

    public AlertOutboxFailurePlan failure(int currentRetryCount, int configuredMaxAttempts, String error) {
        int maxAttempts = maxAttempts(configuredMaxAttempts);
        int nextRetryCount = Math.max(0, currentRetryCount) + 1;
        long exponential = 1L << Math.min(nextRetryCount, 20);
        int delaySeconds = (int) Math.min(3600L, exponential * 30L);
        return new AlertOutboxFailurePlan(
                nextRetryCount,
                delaySeconds,
                nextRetryCount >= maxAttempts,
                error);
    }

    public int maxQueued(int configured) {
        return Math.max(1, configured);
    }

    public int maxRunning(int configured) {
        return Math.max(1, configured);
    }

    public int maxAttempts(int configured) {
        return Math.max(1, configured);
    }

    public int lockTimeoutSeconds(int configured) {
        return Math.max(30, configured);
    }

    public int batchLimit(int configured) {
        return Math.max(1, Math.min(configured, 100));
    }
}
