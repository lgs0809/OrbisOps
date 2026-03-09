package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionAuthoritativeCompletionTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T03:30:00Z"), ZoneOffset.UTC);

    @Test
    void projectionFailuresMustNotTurnCommittedToolSuccessIntoFailure() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        AtomicInteger transactionCommits = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger records = new AtomicInteger();
        AtomicInteger startedCheckpoints = new AtomicInteger();
        RecordingLedger ledger = new RecordingLedger(inTransaction);
        ToolExecutionApplicationService service = service(
                ledger,
                transaction(inTransaction, transactionCommits),
                (target, request) -> {
                    dispatches.incrementAndGet();
                    return Map.of("status", "SUCCEEDED", "rows", 3);
                },
                command -> {
                    assertTrue(inTransaction.get());
                    records.incrementAndGet();
                    return recorded("result-1", command.durationMs());
                },
                (request, type, payload) -> {
                    if ("TOOL_EXECUTION_STARTED".equals(type)) {
                        startedCheckpoints.incrementAndGet();
                        return;
                    }
                    throw new IllegalStateException("checkpoint unavailable");
                },
                event -> {
                    throw new IllegalStateException("audit unavailable");
                });

        ToolExecutionResponse response = service.execute(request());

        assertTrue(response.allowed());
        assertEquals("result-1", response.recorded().resultId());
        assertEquals(1, dispatches.get());
        assertEquals(1, records.get());
        assertEquals(1, transactionCommits.get());
        assertEquals(1, startedCheckpoints.get());
        assertEquals(1, ledger.completions.get());
        assertEquals(0, ledger.failures.get());
        assertNotNull(ledger.completed.projection());
        assertEquals("TOOL_EXECUTION_COMPLETED",
                ledger.completed.projection().checkpointType());
        assertEquals(2, ledger.projectionFailures.size());
        assertTrue(ledger.projectionFailures.stream().anyMatch(command ->
                command.channel() == ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT));
        assertTrue(ledger.projectionFailures.stream().anyMatch(command ->
                command.channel() == ToolExecutionIdempotencyPort.ProjectionChannel.AUDIT));
    }

    @Test
    void ledgerCompletionFailureAfterDispatchMustRequireReviewAndSkipSuccessProjection() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        RecordingLedger ledger = new RecordingLedger(inTransaction);
        ledger.failCompletion = true;
        AtomicInteger failedCheckpoints = new AtomicInteger();
        AtomicInteger failedAudits = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                ledger,
                transaction(inTransaction, new AtomicInteger()),
                (target, request) -> Map.of("status", "SUCCEEDED"),
                command -> recorded("result-1", command.durationMs()),
                (request, type, payload) -> {
                    if ("TOOL_EXECUTION_FAILED".equals(type)) failedCheckpoints.incrementAndGet();
                },
                event -> {
                    if ("failed".equals(event.action())) failedAudits.incrementAndGet();
                });

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.execute(request()));

        assertEquals("ledger completion unavailable", error.getMessage());
        assertEquals(1, ledger.failures.get());
        assertTrue(ledger.failed.uncertainSideEffect());
        assertEquals(0, ledger.projectionFailures.size());
        assertEquals(1, failedCheckpoints.get());
        assertEquals(1, failedAudits.get());
    }

    private ToolExecutionApplicationService service(
            ToolExecutionIdempotencyPort ledger,
            ToolExecutionTransactionPort transactions,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit) {
        ToolExecutionTarget target = new ToolExecutionTarget(
                "code.repair", "code_bash", "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
        AtomicLong nanos = new AtomicLong();
        AtomicInteger callIds = new AtomicInteger();
        return new ToolExecutionApplicationService(
                request -> new ToolExecutionResolution(
                        target, ToolExecutionDecision.allowed("MEDIUM", Map.of())),
                dispatch,
                records,
                checkpoints,
                audit,
                ledger,
                transactions,
                () -> "tool-call-" + callIds.incrementAndGet(),
                () -> nanos.addAndGet(1_000_000L),
                CLOCK);
    }

    private ToolExecutionTransactionPort transaction(
            AtomicBoolean inTransaction,
            AtomicInteger commits) {
        return new ToolExecutionTransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                assertTrue(inTransaction.compareAndSet(false, true));
                try {
                    T result = action.get();
                    commits.incrementAndGet();
                    return result;
                } finally {
                    inTransaction.set(false);
                }
            }
        };
    }

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1",
                "alice",
                "alice",
                "code.repair",
                "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of("command", "pwd"),
                "session-1",
                "run-1",
                Map.of(
                        "idempotencyKey", "run-1:node-1:1:0",
                        "workflowNodeId", "node-1",
                        "workflowAttempt", 1,
                        "workflowToolCallIndex", 0,
                        "metadata", Map.of("_workSessionAttemptId", "attempt-1")),
                Map.of());
    }

    private ToolExecutionRecordedResult recorded(String resultId, long durationMs) {
        return new ToolExecutionRecordedResult(
                resultId,
                "evidence-1",
                "preview",
                "a".repeat(64),
                false,
                "db:" + resultId,
                "b".repeat(64),
                durationMs);
    }

    private static final class RecordingLedger implements ToolExecutionIdempotencyPort {
        private final AtomicBoolean inTransaction;
        private final AtomicInteger completions = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final List<ProjectionFailedCommand> projectionFailures = new ArrayList<>();
        private CompleteCommand completed;
        private FailCommand failed;
        private boolean failCompletion;

        private RecordingLedger(AtomicBoolean inTransaction) {
            this.inTransaction = inTransaction;
        }

        @Override
        public Reservation reserve(ReserveCommand command) {
            return Reservation.execute(1L);
        }

        @Override
        public void complete(CompleteCommand command) {
            assertTrue(inTransaction.get());
            if (failCompletion) {
                throw new IllegalStateException("ledger completion unavailable");
            }
            completed = command;
            completions.incrementAndGet();
        }

        @Override
        public void fail(FailCommand command) {
            failed = command;
            failures.incrementAndGet();
        }

        @Override
        public void projectionFailed(ProjectionFailedCommand command) {
            projectionFailures.add(command);
        }
    }
}
