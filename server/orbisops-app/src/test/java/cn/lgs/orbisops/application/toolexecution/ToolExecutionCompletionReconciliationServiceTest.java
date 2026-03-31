package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionCompletionReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void projectionFailureMustNotPreventIndependentChannelSuccess() {
        RecordingLedger ledger = new RecordingLedger();
        AtomicInteger audits = new AtomicInteger();
        ToolExecutionCompletionReconciliationService service = service(
                ledger,
                (request, type, payload) -> {
                    throw new IllegalStateException("checkpoint unavailable");
                },
                event -> audits.incrementAndGet());

        ToolExecutionCompletionReconciliationService.ProjectionOutcome outcome =
                service.project(projection());

        assertTrue(outcome.failed());
        assertFalse(outcome.checkpointSucceeded());
        assertTrue(outcome.auditSucceeded());
        assertEquals(1, audits.get());
        assertEquals(List.of(ToolExecutionIdempotencyPort.ProjectionChannel.AUDIT),
                ledger.succeededChannels);
        assertEquals(List.of(ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT),
                ledger.failedChannels);
        assertEquals(NOW.plusSeconds(1), ledger.failures.get(0).retryAt());
    }

    @Test
    void reconciliationMustRetryOnlyPendingChannels() {
        RecordingLedger ledger = new RecordingLedger();
        ledger.pending = List.of(new ToolExecutionIdempotencyPort.ProjectionDelivery(
                projection(), true, false, 3));
        AtomicInteger checkpoints = new AtomicInteger();
        AtomicInteger audits = new AtomicInteger();
        ToolExecutionCompletionReconciliationService service = service(
                ledger,
                (request, type, payload) -> checkpoints.incrementAndGet(),
                event -> audits.incrementAndGet());

        ToolExecutionCompletionReconciliationService.ReconciliationOutcome outcome =
                service.reconcile(500);

        assertEquals(1, outcome.scanned());
        assertEquals(1, outcome.checkpointSucceeded());
        assertEquals(0, outcome.auditSucceeded());
        assertEquals(0, outcome.failed());
        assertEquals(1, checkpoints.get());
        assertEquals(0, audits.get());
        assertEquals(List.of(ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT),
                ledger.succeededChannels);
        assertEquals(200, ledger.lastLimit);
    }

    @Test
    void replayedCheckpointMustReconstructFrozenWorkflowIdentity() {
        RecordingLedger ledger = new RecordingLedger();
        List<ToolExecutionRequest> observed = new ArrayList<>();
        ToolExecutionCompletionReconciliationService service = service(
                ledger,
                (request, type, payload) -> observed.add(request),
                event -> { });

        ToolExecutionCompletionReconciliationService.ProjectionOutcome outcome =
                service.project(projection());

        assertFalse(outcome.failed());
        assertEquals(1, observed.size());
        ToolExecutionRequest request = observed.get(0);
        assertEquals("project-1", request.projectId());
        assertEquals("run-1", request.runId());
        assertEquals("node-1", request.requestContext().get("workflowNodeId"));
        assertEquals(2, request.requestContext().get("workflowAttempt"));
        assertEquals("attempt-1",
                ((Map<?, ?>) request.requestContext().get("metadata")).get("_workSessionAttemptId"));
    }

    private ToolExecutionCompletionReconciliationService service(
            ToolExecutionIdempotencyPort ledger,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit) {
        return new ToolExecutionCompletionReconciliationService(
                ledger, checkpoints, audit, CLOCK);
    }

    private ToolExecutionIdempotencyPort.CompletionProjection projection() {
        return new ToolExecutionIdempotencyPort.CompletionProjection(
                "projection-1",
                "project-1",
                "alice",
                "alice",
                "database",
                "query",
                "PRE_APPROVAL_WORKFLOW",
                "session-1",
                "run-1",
                Map.of(
                        "idempotencyKey", "idem-1",
                        "workflowNodeId", "node-1",
                        "workflowAttempt", 2,
                        "workflowToolCallIndex", 0,
                        "metadata", Map.of("_workSessionAttemptId", "attempt-1")),
                "TOOL_EXECUTION_COMPLETED",
                Map.of(
                        "projectionId", "projection-1",
                        "resultId", "result-1"),
                new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        "project-1",
                        "allowed",
                        "database/query",
                        Map.of("projectionId", "projection-1")));
    }

    private static final class RecordingLedger implements ToolExecutionIdempotencyPort {
        private List<ProjectionDelivery> pending = List.of();
        private final List<ProjectionChannel> succeededChannels = new ArrayList<>();
        private final List<ProjectionChannel> failedChannels = new ArrayList<>();
        private final List<ProjectionFailedCommand> failures = new ArrayList<>();
        private int lastLimit;

        @Override
        public Reservation reserve(ReserveCommand command) {
            return Reservation.execute(1L);
        }

        @Override
        public void complete(CompleteCommand command) {
        }

        @Override
        public void fail(FailCommand command) {
        }

        @Override
        public List<ProjectionDelivery> pendingProjections(Instant dueAt, int limit) {
            lastLimit = limit;
            return pending;
        }

        @Override
        public void projectionSucceeded(ProjectionSucceededCommand command) {
            succeededChannels.add(command.channel());
        }

        @Override
        public void projectionFailed(ProjectionFailedCommand command) {
            failedChannels.add(command.channel());
            failures.add(command);
        }
    }
}
