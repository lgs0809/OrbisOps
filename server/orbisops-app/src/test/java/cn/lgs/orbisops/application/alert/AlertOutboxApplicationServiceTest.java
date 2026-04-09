package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertOutboxRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxEntry;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxStatus;
import cn.lgs.orbisops.domain.alert.model.AlertRunRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertOutboxApplicationServiceTest {

    @Test
    void enqueuePreemptsLowerPriorityAndWritesInsideOneTransaction() {
        FakeRepository repository = new FakeRepository();
        repository.activeCount = 10;
        CountingTransactions transactions = new CountingTransactions();
        AlertOutboxApplicationService service = service(repository, transactions, true);

        service.enqueue(draft(100), 10);

        assertTrue(repository.preempted);
        assertEquals(1, repository.upsertCount);
        assertEquals(1, transactions.count);
    }

    @Test
    void enqueueRejectsNonCriticalWhenProjectQueueIsFull() {
        FakeRepository repository = new FakeRepository();
        repository.activeCount = 10;
        AlertOutboxApplicationService service = service(repository, new CountingTransactions(), true);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.enqueue(draft(99), 10));

        assertEquals("ALERT_PROJECT_QUEUE_FULL:项目告警队列已满", error.getMessage());
        assertEquals(0, repository.upsertCount);
    }

    @Test
    void submissionFailureUsesTypedRetryPlan() {
        FakeRepository repository = new FakeRepository();
        repository.entry = entry("project-a", 0);
        AlertOutboxApplicationService service = service(repository, new CountingTransactions(), true);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.dispatch("dispatch-1", 3, request -> {
                    throw new RuntimeException("network");
                }));

        assertEquals("network", error.getMessage());
        assertEquals(1, repository.markFailedCount);
        assertEquals(1, repository.failure.nextRetryCount());
        assertEquals(60, repository.failure.retryDelaySeconds());
        assertFalse(repository.failure.deadLetter());
    }

    @Test
    void successCasConflictDoesNotWriteFailureState() {
        FakeRepository repository = new FakeRepository();
        repository.entry = entry("project-a", 0);
        repository.markSucceededResult = false;
        AlertOutboxApplicationService service = service(repository, new CountingTransactions(), true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.dispatch("dispatch-1", 3, request -> "run-1"));

        assertEquals("ALERT_OUTBOX_SUCCESS_CONFLICT:dispatch-1", error.getMessage());
        assertEquals(0, repository.markFailedCount);
    }

    @Test
    void pendingBatchRecoversDeadLettersAndRespectsProjectCapacity() {
        FakeRepository repository = new FakeRepository();
        repository.entries = List.of(entry("project-a", 0), entry("project-b", 1));
        repository.recovered = 2;
        repository.deadLettered = 3;
        AlertOutboxApplicationService service = new AlertOutboxApplicationService(
                repository,
                new SequenceIdentity(),
                (projectId, maxRunning) -> "project-a".equals(projectId),
                new CountingTransactions());

        AlertOutboxBatchResult result = service.processPending(
                999, 4, 1, 0, request -> "run-1");

        assertEquals(2, result.scanned());
        assertEquals(1, result.submitted());
        assertEquals(0, result.failed());
        assertEquals(2, result.recovered());
        assertEquals(3, result.deadLettered());
        assertEquals(100, repository.lastLimit);
        assertEquals(4, repository.lastMaxAttempts);
        assertEquals(30, repository.lastLockTimeout);
    }

    private AlertOutboxApplicationService service(
            FakeRepository repository,
            CountingTransactions transactions,
            boolean capacity) {
        return new AlertOutboxApplicationService(
                repository,
                new SequenceIdentity(),
                (projectId, maxRunning) -> capacity,
                transactions);
    }

    private AlertOutboxDraft draft(int priority) {
        return new AlertOutboxDraft(
                "dispatch-1",
                7L,
                "project-a",
                "fingerprint-1",
                "aggregate-1",
                AlertAggregateEventType.FIRST,
                priority,
                request(),
                java.util.Map.of("status", "firing"));
    }

    private AlertOutboxEntry entry(String projectId, int retryCount) {
        return new AlertOutboxEntry(
                "project-a".equals(projectId) ? 1L : 2L,
                "project-a".equals(projectId) ? "dispatch-1" : "dispatch-2",
                7L,
                projectId,
                "fingerprint-1",
                "aggregate-1",
                AlertAggregateEventType.FIRST,
                AlertOutboxStatus.PENDING,
                request(),
                retryCount,
                50);
    }

    private AlertRunRequest request() {
        return new AlertRunRequest(
                "",
                "alertmanager:rule-7",
                "project-a",
                "agent-a",
                3,
                "{}",
                "",
                "analyse alert",
                30,
                "5m",
                true,
                null,
                5,
                120,
                20,
                false,
                "",
                "",
                "WORKFLOW",
                "ALERTMANAGER",
                "fingerprint-1");
    }

    private static final class CountingTransactions implements AlertOutboxTransactionPort {
        private int count;

        @Override
        public <T> T required(Supplier<T> action) {
            count++;
            return action.get();
        }
    }

    private static final class SequenceIdentity implements Supplier<String> {
        private int sequence;

        @Override
        public String get() {
            sequence++;
            return "lease-" + sequence;
        }
    }

    private static final class FakeRepository implements IAlertOutboxRepository {
        private long activeCount;
        private boolean preempted;
        private int upsertCount;
        private AlertOutboxEntry entry;
        private List<AlertOutboxEntry> entries = new ArrayList<>();
        private boolean markSucceededResult = true;
        private int markFailedCount;
        private AlertOutboxFailurePlan failure;
        private int recovered;
        private int deadLettered;
        private int lastMaxAttempts;
        private int lastLimit;
        private int lastLockTimeout;

        @Override
        public long countActiveByProject(String projectId) {
            return activeCount;
        }

        @Override
        public boolean preemptOneLowerPriority(String projectId, int priority) {
            preempted = true;
            return true;
        }

        @Override
        public void upsert(AlertOutboxDraft draft) {
            upsertCount++;
        }

        @Override
        public Optional<AlertOutboxEntry> findByDispatchKey(String dispatchKey) {
            return Optional.ofNullable(entry);
        }

        @Override
        public List<AlertOutboxEntry> listDispatchable(int maxAttempts, int limit) {
            lastMaxAttempts = maxAttempts;
            lastLimit = limit;
            return entries;
        }

        @Override
        public boolean claim(long id, String claimId, int maxAttempts) {
            lastMaxAttempts = maxAttempts;
            return true;
        }

        @Override
        public boolean markSucceeded(long id, String claimId, String runId) {
            return markSucceededResult;
        }

        @Override
        public boolean markFailed(long id, String claimId, AlertOutboxFailurePlan failure) {
            markFailedCount++;
            this.failure = failure;
            return true;
        }

        @Override
        public int recoverStale(int lockTimeoutSeconds, int maxAttempts) {
            lastLockTimeout = lockTimeoutSeconds;
            lastMaxAttempts = maxAttempts;
            return recovered;
        }

        @Override
        public int deadLetterExhausted(int maxAttempts) {
            lastMaxAttempts = maxAttempts;
            return deadLettered;
        }
    }
}
