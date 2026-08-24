package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertAggregationRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSeed;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateState;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationDecision;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertAggregationApplicationServiceTest {

    @Test
    void recordRunsInsertLockPolicyUpdateAndReadInOneUnitOfWork() {
        FakeRepository repository = new FakeRepository();
        CountingTransactions transactions = new CountingTransactions();
        AlertAggregationApplicationService service = new AlertAggregationApplicationService(
                repository, () -> "claim-1", transactions);

        AlertAggregationDecision decision = service.record(
                "project-a", 7L, "fingerprint-1", "firing", "warning", "mysql",
                Map.of("status", "firing"), 120, 900);

        assertEquals(AlertAggregateEventType.FIRST, decision.eventType());
        assertTrue(decision.dispatchNow());
        assertEquals(1, transactions.count);
        assertEquals(1, repository.applyCount);
        assertEquals(3, repository.lockCount);
        assertEquals(1, repository.insertCount);
    }

    @Test
    void existingAggregateDoesNotAcquireInsertDuplicateKeyLockBeforeItsExclusiveLock() {
        FakeRepository repository = new FakeRepository();
        AlertAggregationApplicationService service = new AlertAggregationApplicationService(
                repository, () -> "claim-1", new CountingTransactions());
        service.record("project-a", 7L, "fingerprint-1", "firing", "warning", "mysql", Map.of(), 120, 900);
        AlertAggregationDecision second = service.record(
                "project-a", 7L, "fingerprint-1", "firing", "warning", "mysql", Map.of(), 120, 900);
        assertEquals(AlertAggregateEventType.DUPLICATE, second.eventType());
        assertEquals(1, repository.insertCount);
    }

    @Test
    void versionConflictFailsClosedInsideTransaction() {
        FakeRepository repository = new FakeRepository();
        repository.applyResult = false;
        AlertAggregationApplicationService service = new AlertAggregationApplicationService(
                repository, () -> "claim-1", new CountingTransactions());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.record(
                "project-a", 7L, "fingerprint-1", "firing", "warning", "mysql",
                Map.of(), 120, 900));

        assertTrue(error.getMessage().startsWith("ALERT_AGGREGATE_VERSION_CONFLICT:"));
    }

    @Test
    void dueSummaryClaimUsesBoundedQueryAndCas() {
        FakeRepository repository = new FakeRepository();
        repository.inserted = false;
        repository.due = List.of(snapshot("aggregate-1", AlertAggregateState.FIRING, 3, 4, 9));
        CountingTransactions transactions = new CountingTransactions();
        AlertAggregationApplicationService service = new AlertAggregationApplicationService(
                repository, () -> "claim-1", transactions);

        List<AlertSummaryClaim> claims = service.claimDueSummaries(999, 1);

        assertEquals(1, claims.size());
        assertEquals("claim-1", claims.get(0).claimToken());
        assertEquals(10, claims.get(0).version());
        assertEquals(100, repository.lastLimit);
        assertEquals(30, repository.lastStaleSeconds);
        assertEquals(1, transactions.count);
    }

    @Test
    void acknowledgementConflictIsExplicit() {
        FakeRepository repository = new FakeRepository();
        repository.ackResult = false;
        AlertAggregationApplicationService service = new AlertAggregationApplicationService(
                repository, () -> "claim-1", new CountingTransactions());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.acknowledgeSummary(claim(), 120));

        assertEquals("ALERT_SUMMARY_ACK_CONFLICT:aggregate-1", error.getMessage());
    }

    private AlertSummaryClaim claim() {
        return new AlertSummaryClaim(
                "aggregate-1", "dispatch-1", "project-a", 7L, "fingerprint-1",
                "WARNING", 50, 4, 3, Map.of(), List.of("mysql"), 10, "claim-1");
    }

    private static AlertAggregateSnapshot snapshot(
            String key,
            AlertAggregateState state,
            long occurrences,
            int pending,
            long version) {
        return new AlertAggregateSnapshot(
                key, "project-a", 7L, "fingerprint-1", state,
                "WARNING", 50, occurrences, pending,
                List.of("mysql"), Map.of("status", state.name()), version);
    }

    private static final class CountingTransactions implements AlertAggregationTransactionPort {
        private int count;

        @Override
        public <T> T required(Supplier<T> action) {
            count++;
            return action.get();
        }
    }

    private static final class FakeRepository implements IAlertAggregationRepository {
        private AlertAggregateSnapshot current;
        private boolean inserted = true;
        private boolean applyResult = true;
        private boolean ackResult = true;
        private int applyCount;
        private int insertCount;
        private int lockCount;
        private int lastLimit;
        private int lastStaleSeconds;
        private List<AlertAggregateSnapshot> due = new ArrayList<>();

        @Override
        public boolean insertIfAbsent(AlertAggregateSeed seed) {
            insertCount++;
            if (current == null) {
                current = new AlertAggregateSnapshot(
                        seed.aggregateKey(), seed.projectId(), seed.ruleId(), seed.fingerprint(),
                        seed.state(), seed.severity(), seed.severityRank(), 1, 0,
                        seed.affectedResources(), seed.payload(), 1);
            }
            return inserted;
        }

        @Override
        public Optional<AlertAggregateSnapshot> lock(String aggregateKey) {
            lockCount++;
            return Optional.ofNullable(current);
        }

        @Override
        public boolean apply(AlertAggregationPlan plan) {
            applyCount++;
            if (!applyResult) return false;
            current = new AlertAggregateSnapshot(
                    current.aggregateKey(), current.projectId(), current.ruleId(), current.fingerprint(),
                    plan.targetState(), plan.severity(), plan.severityRank(),
                    current.occurrenceCount(), current.pendingSummaryCount(),
                    plan.affectedResources(), plan.payload(), current.version() + 1);
            return true;
        }

        @Override
        public List<AlertAggregateSnapshot> lockDueSummaries(int limit, int staleClaimSeconds) {
            lastLimit = limit;
            lastStaleSeconds = staleClaimSeconds;
            return due;
        }

        @Override
        public boolean claimSummary(
                AlertAggregateSnapshot aggregate,
                String claimToken,
                int staleClaimSeconds) {
            return true;
        }

        @Override
        public boolean acknowledgeSummary(AlertSummaryClaim claim, int debounceSeconds) {
            return ackResult;
        }

        @Override
        public void releaseSummaryClaim(AlertSummaryClaim claim) {
        }
    }
}
