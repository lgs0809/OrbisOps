package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertOutboxRepository;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxEntry;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxQuotaDecision;
import cn.lgs.orbisops.domain.alert.service.AlertOutboxPolicy;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class AlertOutboxApplicationService {

    private final IAlertOutboxRepository outbox;
    private final Supplier<String> claimIdSupplier;
    private final AlertOutboxProjectCapacityPort capacity;
    private final AlertOutboxTransactionPort transactions;
    private final AlertOutboxPolicy policy;

    public AlertOutboxApplicationService(
            IAlertOutboxRepository outbox,
            Supplier<String> claimIdSupplier,
            AlertOutboxProjectCapacityPort capacity,
            AlertOutboxTransactionPort transactions) {
        if (outbox == null) throw new IllegalArgumentException("ALERT_OUTBOX_REPOSITORY_REQUIRED");
        if (claimIdSupplier == null) throw new IllegalArgumentException("ALERT_OUTBOX_CLAIM_ID_SUPPLIER_REQUIRED");
        if (capacity == null) throw new IllegalArgumentException("ALERT_OUTBOX_CAPACITY_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("ALERT_OUTBOX_TRANSACTION_REQUIRED");
        this.outbox = outbox;
        this.claimIdSupplier = claimIdSupplier;
        this.capacity = capacity;
        this.transactions = transactions;
        this.policy = new AlertOutboxPolicy();
    }

    public void enqueue(AlertOutboxDraft draft, int configuredProjectMaxQueued) {
        if (draft == null) throw new IllegalArgumentException("ALERT_OUTBOX_DRAFT_REQUIRED");
        transactions.required(() -> {
            long active = outbox.countActiveByProject(draft.projectId());
            AlertOutboxQuotaDecision decision = policy.quota(
                    active, configuredProjectMaxQueued, draft.priority());
            if (decision == AlertOutboxQuotaDecision.REJECT) {
                throw new IllegalStateException("ALERT_PROJECT_QUEUE_FULL:项目告警队列已满");
            }
            if (decision == AlertOutboxQuotaDecision.PREEMPT_LOWER_PRIORITY
                    && !outbox.preemptOneLowerPriority(draft.projectId(), draft.priority())) {
                throw new IllegalStateException("ALERT_PROJECT_QUEUE_FULL:Critical 告警无法抢占队列容量");
            }
            outbox.upsert(draft);
            return Boolean.TRUE;
        });
    }

    public Optional<String> dispatchIfCapacity(
            String projectId,
            int configuredProjectMaxRunning,
            String dispatchKey,
            int configuredMaxAttempts,
            AlertOutboxSubmissionPort submission) {
        String project = required(projectId, "ALERT_OUTBOX_PROJECT_ID_REQUIRED");
        if (!capacity.canDispatch(project, policy.maxRunning(configuredProjectMaxRunning))) {
            return Optional.empty();
        }
        return Optional.of(dispatch(dispatchKey, configuredMaxAttempts, submission));
    }

    public String dispatch(
            String dispatchKey,
            int configuredMaxAttempts,
            AlertOutboxSubmissionPort submission) {
        if (submission == null) throw new IllegalArgumentException("ALERT_OUTBOX_SUBMISSION_REQUIRED");
        String key = required(dispatchKey, "ALERT_OUTBOX_DISPATCH_KEY_REQUIRED");
        int maxAttempts = policy.maxAttempts(configuredMaxAttempts);
        AlertOutboxEntry entry = outbox.findByDispatchKey(key)
                .orElseThrow(() -> new IllegalStateException("ALERT_OUTBOX_NOT_FOUND:" + key));
        String leaseId = claimIdSupplier.get();
        if (!outbox.claim(entry.id(), leaseId, maxAttempts)) {
            throw new IllegalStateException("ALERT_OUTBOX_CLAIM_CONFLICT:" + key);
        }
        return submitClaimed(entry, leaseId, maxAttempts, submission);
    }

    public AlertOutboxBatchResult processPending(
            int configuredLimit,
            int configuredMaxAttempts,
            int configuredLockTimeoutSeconds,
            int configuredProjectMaxRunning,
            AlertOutboxSubmissionPort submission) {
        if (submission == null) throw new IllegalArgumentException("ALERT_OUTBOX_SUBMISSION_REQUIRED");
        int limit = policy.batchLimit(configuredLimit);
        int maxAttempts = policy.maxAttempts(configuredMaxAttempts);
        int lockTimeout = policy.lockTimeoutSeconds(configuredLockTimeoutSeconds);
        int maxRunning = policy.maxRunning(configuredProjectMaxRunning);
        int recovered = outbox.recoverStale(lockTimeout, maxAttempts);
        int deadLettered = outbox.deadLetterExhausted(maxAttempts);
        List<AlertOutboxEntry> entries = outbox.listDispatchable(maxAttempts, limit);
        int submitted = 0;
        int failed = 0;
        for (AlertOutboxEntry entry : entries) {
            if (!capacity.canDispatch(entry.projectId(), maxRunning)) continue;
            String leaseId = claimIdSupplier.get();
            if (!outbox.claim(entry.id(), leaseId, maxAttempts)) continue;
            try {
                submitClaimed(entry, leaseId, maxAttempts, submission);
                submitted++;
            } catch (Exception ignored) {
                failed++;
            }
        }
        return new AlertOutboxBatchResult(
                entries.size(), submitted, failed, recovered, deadLettered);
    }

    private String submitClaimed(
            AlertOutboxEntry entry,
            String leaseId,
            int maxAttempts,
            AlertOutboxSubmissionPort submission) {
        final String runId;
        try {
            runId = required(
                    submission.submit(entry.request()),
                    "ALERT_OUTBOX_RUN_ID_REQUIRED");
        } catch (Exception error) {
            AlertOutboxFailurePlan failure = policy.failure(
                    entry.retryCount(), maxAttempts, error.getMessage());
            if (!outbox.markFailed(entry.id(), leaseId, failure)) {
                throw new IllegalStateException(
                        "ALERT_OUTBOX_FAILURE_CONFLICT:" + entry.dispatchKey(), error);
            }
            throw error instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(failure.errorMessage(), error);
        }
        if (!outbox.markSucceeded(entry.id(), leaseId, runId)) {
            throw new IllegalStateException("ALERT_OUTBOX_SUCCESS_CONFLICT:" + entry.dispatchKey());
        }
        return runId;
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
