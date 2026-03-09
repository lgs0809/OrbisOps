package cn.lgs.orbisops.application.worksession;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkSessionRecoveryExecutionProcessManagerTest {

    @Test
    void onlyRecoverableDecisionsMustBeExecuted() {
        WorkSessionRecoveryPort.RecoveryDecision recoverable = decision(
                "run-1", "RECOVERABLE", "LEASE_EXPIRED_SAFE_CHECKPOINT");
        WorkSessionRecoveryPort.RecoveryDecision review = decision(
                "run-2", "RECOVERY_REVIEW_REQUIRED", "SIDE_EFFECT_AMBIGUOUS");
        AtomicInteger executions = new AtomicInteger();
        WorkSessionProcessManager manager = new WorkSessionProcessManager(
                limit -> List.of(recoverable, review),
                decision -> {
                    executions.incrementAndGet();
                    return WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.scheduled(decision);
                });

        WorkSessionProcessManager.RecoveryBatchOutcome outcome =
                manager.recoverAndExecuteExpiredLeases(20);

        assertEquals(List.of(recoverable, review), outcome.decisions());
        assertEquals(1, outcome.executions().size());
        assertEquals("run-1", outcome.executions().get(0).runId());
        assertEquals("SCHEDULED", outcome.executions().get(0).status());
        assertEquals(1, executions.get());
    }

    @Test
    void executionFailureMustBeMaterializedWithoutStoppingOtherRecoveries() {
        WorkSessionRecoveryPort.RecoveryDecision first = decision(
                "run-1", "RECOVERABLE", "LEASE_EXPIRED_SAFE_CHECKPOINT");
        WorkSessionRecoveryPort.RecoveryDecision second = decision(
                "run-2", "RECOVERABLE", "LEASE_EXPIRED_SAFE_CHECKPOINT");
        AtomicInteger calls = new AtomicInteger();
        WorkSessionProcessManager manager = new WorkSessionProcessManager(
                limit -> List.of(first, second),
                decision -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new IllegalStateException("executor rejected");
                    }
                    return WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.scheduled(decision);
                });

        WorkSessionProcessManager.RecoveryBatchOutcome outcome =
                manager.recoverAndExecuteExpiredLeases(10);

        assertEquals(2, outcome.executions().size());
        assertEquals("FAILED", outcome.executions().get(0).status());
        assertEquals("EXECUTOR_REJECTED", outcome.executions().get(0).reasonCode());
        assertEquals("SCHEDULED", outcome.executions().get(1).status());
    }

    @Test
    void nullExecutionOutcomeMustFailClosedAsTypedOutcome() {
        WorkSessionRecoveryPort.RecoveryDecision recoverable = decision(
                "run-1", "RECOVERABLE", "LEASE_EXPIRED_SAFE_CHECKPOINT");
        WorkSessionProcessManager manager = new WorkSessionProcessManager(
                limit -> List.of(recoverable),
                decision -> null);

        WorkSessionProcessManager.RecoveryBatchOutcome outcome =
                manager.recoverAndExecuteExpiredLeases(10);

        assertEquals("FAILED", outcome.executions().get(0).status());
        assertEquals("WORK_SESSION_RECOVERY_EXECUTION_RESULT_REQUIRED",
                outcome.executions().get(0).reasonCode());
    }

    @Test
    void invalidBatchMustRemainRejected() {
        WorkSessionProcessManager manager = new WorkSessionProcessManager(
                limit -> List.of(),
                decision -> WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.skipped(decision));

        assertThrows(IllegalArgumentException.class,
                () -> manager.recoverAndExecuteExpiredLeases(0));
        assertThrows(IllegalArgumentException.class,
                () -> manager.recoverAndExecuteExpiredLeases(1001));
    }

    private WorkSessionRecoveryPort.RecoveryDecision decision(
            String runId,
            String status,
            String reason) {
        return new WorkSessionRecoveryPort.RecoveryDecision(
                runId, "project-1", "attempt-1", status, reason);
    }
}
