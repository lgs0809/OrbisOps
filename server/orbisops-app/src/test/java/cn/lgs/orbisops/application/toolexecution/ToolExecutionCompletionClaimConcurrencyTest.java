package cn.lgs.orbisops.application.toolexecution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionCompletionClaimConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-08-03T05:00:00Z");

    @Test
    void twoInstancesMustDeliverOneProjectionOnlyOnce() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        LeaseLedger ledger = new LeaseLedger(projection(), NOW);
        AtomicInteger checkpoints = new AtomicInteger();
        AtomicInteger audits = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ToolExecutionCompletionReconciliationService first = service(
                ledger, clock, "worker-a", checkpoints, audits);
        ToolExecutionCompletionReconciliationService second = service(
                ledger, clock, "worker-b", checkpoints, audits);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> left = executor.submit(() -> {
                await(start);
                first.reconcile(10);
            });
            Future<?> right = executor.submit(() -> {
                await(start);
                second.reconcile(10);
            });
            start.countDown();
            left.get();
            right.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, checkpoints.get());
        assertEquals(1, audits.get());
        assertEquals("SUCCEEDED", ledger.checkpointStatus);
        assertEquals("SUCCEEDED", ledger.auditStatus);
        assertEquals(1L, ledger.fencingToken);
        assertTrue(ledger.ownerToken.isBlank());
    }

    @Test
    void expiredLeaseMustIncrementFenceAndRejectStaleOwner() {
        MutableClock clock = new MutableClock(NOW);
        LeaseLedger ledger = new LeaseLedger(projection(), NOW);
        ToolExecutionIdempotencyPort.ProjectionDelivery first = ledger.claimProjection(
                new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                        projection(), "worker-a", NOW, NOW.plusSeconds(30)))
                .orElseThrow();

        assertTrue(ledger.claimProjection(
                new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                        projection(), "worker-b", NOW.plusSeconds(10), NOW.plusSeconds(40)))
                .isEmpty());

        clock.advance(Duration.ofSeconds(31));
        ToolExecutionIdempotencyPort.ProjectionDelivery takeover = ledger.claimProjection(
                new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                        projection(), "worker-b", clock.instant(), clock.instant().plusSeconds(30)))
                .orElseThrow();

        assertEquals(1L, first.fencingToken());
        assertEquals(2L, takeover.fencingToken());
        assertThrows(IllegalStateException.class, () -> ledger.projectionSucceeded(
                new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                        "projection-1",
                        ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                        first.ownerToken(),
                        first.fencingToken(),
                        clock.instant())));
        ledger.projectionSucceeded(
                new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                        "projection-1",
                        ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                        takeover.ownerToken(),
                        takeover.fencingToken(),
                        clock.instant()));
        assertEquals("SUCCEEDED", ledger.checkpointStatus);
    }

    @Test
    void failedChannelMustRetryWithoutRepeatingSucceededChannel() {
        MutableClock clock = new MutableClock(NOW);
        LeaseLedger ledger = new LeaseLedger(projection(), NOW);
        AtomicInteger checkpoints = new AtomicInteger();
        AtomicInteger audits = new AtomicInteger();
        ToolExecutionCompletionReconciliationService failing =
                new ToolExecutionCompletionReconciliationService(
                        ledger,
                        (request, type, payload) -> {
                            checkpoints.incrementAndGet();
                            throw new IllegalStateException("checkpoint unavailable");
                        },
                        event -> audits.incrementAndGet(),
                        clock,
                        () -> "worker-a",
                        Duration.ofSeconds(30));

        ToolExecutionCompletionReconciliationService.ProjectionOutcome first =
                failing.project(projection());

        assertTrue(first.failed());
        assertEquals(1, checkpoints.get());
        assertEquals(1, audits.get());
        assertEquals("FAILED", ledger.checkpointStatus);
        assertEquals("SUCCEEDED", ledger.auditStatus);

        clock.advance(Duration.ofSeconds(1));
        ToolExecutionCompletionReconciliationService retry = service(
                ledger, clock, "worker-b", checkpoints, audits);
        ToolExecutionCompletionReconciliationService.ReconciliationOutcome outcome =
                retry.reconcile(10);

        assertEquals(1, outcome.scanned());
        assertEquals(1, outcome.checkpointSucceeded());
        assertEquals(0, outcome.auditSucceeded());
        assertEquals(2, checkpoints.get());
        assertEquals(1, audits.get());
    }

    private ToolExecutionCompletionReconciliationService service(
            LeaseLedger ledger,
            Clock clock,
            String owner,
            AtomicInteger checkpoints,
            AtomicInteger audits) {
        return new ToolExecutionCompletionReconciliationService(
                ledger,
                (request, type, payload) -> checkpoints.incrementAndGet(),
                event -> audits.incrementAndGet(),
                clock,
                () -> owner,
                Duration.ofSeconds(30));
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
                Map.of("workflowNodeId", "node-1"),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("resultId", "result-1"),
                new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        "project-1", "allowed", "database/query", Map.of()));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static final class LeaseLedger implements ToolExecutionIdempotencyPort {
        private final CompletionProjection projection;
        private String checkpointStatus = "PENDING";
        private String auditStatus = "PENDING";
        private String ownerToken = "";
        private long fencingToken;
        private Instant leaseExpiresAt;
        private Instant nextAttemptAt;
        private int attempts;

        private LeaseLedger(CompletionProjection projection, Instant nextAttemptAt) {
            this.projection = projection;
            this.nextAttemptAt = nextAttemptAt;
        }

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
        public synchronized Optional<ProjectionDelivery> claimProjection(
                ProjectionClaimCommand command) {
            if (!projection.projectionId().equals(command.projection().projectionId())) {
                return Optional.empty();
            }
            if (!pending() || nextAttemptAt.isAfter(command.claimedAt())) {
                return Optional.empty();
            }
            if (!ownerToken.isBlank()
                    && leaseExpiresAt != null
                    && leaseExpiresAt.isAfter(command.claimedAt())) {
                return Optional.empty();
            }
            ownerToken = command.ownerToken();
            fencingToken++;
            leaseExpiresAt = command.leaseExpiresAt();
            return Optional.of(delivery());
        }

        @Override
        public synchronized List<ProjectionDelivery> claimProjections(
                ProjectionBatchClaimCommand command) {
            return claimProjection(new ProjectionClaimCommand(
                    projection,
                    command.ownerToken(),
                    command.claimedAt(),
                    command.leaseExpiresAt())).stream().toList();
        }

        @Override
        public synchronized void projectionSucceeded(ProjectionSucceededCommand command) {
            assertOwner(command.ownerToken(), command.fencingToken());
            if (command.channel() == ProjectionChannel.CHECKPOINT) {
                checkpointStatus = "SUCCEEDED";
            } else {
                auditStatus = "SUCCEEDED";
            }
        }

        @Override
        public synchronized void projectionFailed(ProjectionFailedCommand command) {
            assertOwner(command.ownerToken(), command.fencingToken());
            if (command.channel() == ProjectionChannel.CHECKPOINT) {
                checkpointStatus = "FAILED";
            } else {
                auditStatus = "FAILED";
            }
            attempts++;
            nextAttemptAt = command.retryAt();
        }

        @Override
        public synchronized void releaseProjection(ProjectionReleaseCommand command) {
            assertOwner(command.ownerToken(), command.fencingToken());
            ownerToken = "";
            leaseExpiresAt = null;
        }

        private ProjectionDelivery delivery() {
            return new ProjectionDelivery(
                    projection,
                    !"SUCCEEDED".equals(checkpointStatus),
                    !"SUCCEEDED".equals(auditStatus),
                    attempts,
                    ownerToken,
                    fencingToken,
                    leaseExpiresAt);
        }

        private boolean pending() {
            return !"SUCCEEDED".equals(checkpointStatus)
                    || !"SUCCEEDED".equals(auditStatus);
        }

        private void assertOwner(String owner, long fence) {
            if (!ownerToken.equals(owner) || fencingToken != fence) {
                throw new IllegalStateException("TOOL_COMPLETION_PROJECTION_FENCED");
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
