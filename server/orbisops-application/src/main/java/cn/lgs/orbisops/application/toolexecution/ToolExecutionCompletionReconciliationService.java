package cn.lgs.orbisops.application.toolexecution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Replays Checkpoint and Audit projections from the authoritative completion outbox.
 * Projection failures never change an already committed ToolResult/Evidence outcome.
 */
public final class ToolExecutionCompletionReconciliationService {

    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private static final Duration DEFAULT_CLAIM_LEASE = Duration.ofSeconds(30);

    private final ToolExecutionIdempotencyPort ledger;
    private final ToolExecutionCheckpointPort checkpoints;
    private final ToolExecutionAuditPort audit;
    private final Clock clock;
    private final Supplier<String> ownerTokenSupplier;
    private final Duration claimLease;

    public ToolExecutionCompletionReconciliationService(
            ToolExecutionIdempotencyPort ledger,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            Clock clock) {
        this(
                ledger,
                checkpoints,
                audit,
                clock,
                () -> "tool-completion-" + UUID.randomUUID(),
                DEFAULT_CLAIM_LEASE);
    }

    public ToolExecutionCompletionReconciliationService(
            ToolExecutionIdempotencyPort ledger,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            Clock clock,
            Supplier<String> ownerTokenSupplier,
            Duration claimLease) {
        if (ledger == null) throw new IllegalArgumentException("TOOL_COMPLETION_LEDGER_REQUIRED");
        if (checkpoints == null) throw new IllegalArgumentException("TOOL_COMPLETION_CHECKPOINT_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("TOOL_COMPLETION_AUDIT_PORT_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("TOOL_COMPLETION_CLOCK_REQUIRED");
        if (ownerTokenSupplier == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_OWNER_SUPPLIER_REQUIRED");
        }
        if (claimLease == null || claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("TOOL_COMPLETION_CLAIM_LEASE_INVALID");
        }
        this.ledger = ledger;
        this.checkpoints = checkpoints;
        this.audit = audit;
        this.clock = clock;
        this.ownerTokenSupplier = ownerTokenSupplier;
        this.claimLease = claimLease;
    }

    public ProjectionOutcome project(
            ToolExecutionIdempotencyPort.CompletionProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_REQUIRED");
        }
        Instant now = clock.instant();
        String ownerToken = ownerToken();
        return ledger.claimProjection(
                        new ToolExecutionIdempotencyPort.ProjectionClaimCommand(
                                projection,
                                ownerToken,
                                now,
                                now.plus(claimLease)))
                .map(this::deliver)
                .orElseGet(ProjectionOutcome::notClaimed);
    }

    public ReconciliationOutcome reconcile(int limit) {
        Instant now = clock.instant();
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        List<ToolExecutionIdempotencyPort.ProjectionDelivery> pending =
                ledger.claimProjections(
                        new ToolExecutionIdempotencyPort.ProjectionBatchClaimCommand(
                                ownerToken(),
                                now,
                                now.plus(claimLease),
                                boundedLimit));
        int checkpointSucceeded = 0;
        int auditSucceeded = 0;
        int failed = 0;
        for (ToolExecutionIdempotencyPort.ProjectionDelivery delivery : pending) {
            ProjectionOutcome outcome = deliver(delivery);
            if (outcome.checkpointSucceeded()) checkpointSucceeded++;
            if (outcome.auditSucceeded()) auditSucceeded++;
            if (outcome.failed()) failed++;
        }
        return new ReconciliationOutcome(
                pending.size(), checkpointSucceeded, auditSucceeded, failed);
    }

    private ProjectionOutcome deliver(
            ToolExecutionIdempotencyPort.ProjectionDelivery delivery) {
        ToolExecutionIdempotencyPort.CompletionProjection projection = delivery.projection();
        boolean checkpointSucceeded = false;
        boolean auditSucceeded = false;
        boolean failed = false;
        try {
            if (delivery.checkpointPending()) {
                try {
                    checkpoints.checkpoint(
                            projection.request(),
                            projection.checkpointType(),
                            projection.checkpointPayload());
                    ledger.projectionSucceeded(
                            new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                                    projection.projectionId(),
                                    ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                                    delivery.ownerToken(),
                                    delivery.fencingToken(),
                                    clock.instant()));
                    checkpointSucceeded = true;
                } catch (RuntimeException error) {
                    failed = true;
                    markFailed(
                            delivery,
                            ToolExecutionIdempotencyPort.ProjectionChannel.CHECKPOINT,
                            error);
                }
            }
            if (delivery.auditPending()) {
                try {
                    audit.record(projection.auditEvent());
                    ledger.projectionSucceeded(
                            new ToolExecutionIdempotencyPort.ProjectionSucceededCommand(
                                    projection.projectionId(),
                                    ToolExecutionIdempotencyPort.ProjectionChannel.AUDIT,
                                    delivery.ownerToken(),
                                    delivery.fencingToken(),
                                    clock.instant()));
                    auditSucceeded = true;
                } catch (RuntimeException error) {
                    failed = true;
                    markFailed(
                            delivery,
                            ToolExecutionIdempotencyPort.ProjectionChannel.AUDIT,
                            error);
                }
            }
            return new ProjectionOutcome(checkpointSucceeded, auditSucceeded, failed);
        } finally {
            release(delivery);
        }
    }

    private void markFailed(
            ToolExecutionIdempotencyPort.ProjectionDelivery delivery,
            ToolExecutionIdempotencyPort.ProjectionChannel channel,
            RuntimeException error) {
        Instant failedAt = clock.instant();
        try {
            ledger.projectionFailed(
                    new ToolExecutionIdempotencyPort.ProjectionFailedCommand(
                            delivery.projection().projectionId(),
                            channel,
                            delivery.ownerToken(),
                            delivery.fencingToken(),
                            failureMessage(error),
                            failedAt,
                            failedAt.plus(retryDelay(delivery.attempts()))));
        } catch (RuntimeException ignored) {
            // The row remains owned until release or lease expiry and will be retried later.
        }
    }

    private void release(ToolExecutionIdempotencyPort.ProjectionDelivery delivery) {
        if (!delivery.claimed()) return;
        try {
            ledger.releaseProjection(
                    new ToolExecutionIdempotencyPort.ProjectionReleaseCommand(
                            delivery.projection().projectionId(),
                            delivery.ownerToken(),
                            delivery.fencingToken(),
                            clock.instant()));
        } catch (RuntimeException ignored) {
            // A failed release is recovered by lease expiry; stale owners remain fenced.
        }
    }

    private String ownerToken() {
        String owner = ownerTokenSupplier.get();
        String normalized = owner == null ? "" : owner.trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException("TOOL_COMPLETION_OWNER_TOKEN_REQUIRED");
        }
        return normalized;
    }

    private Duration retryDelay(int attempts) {
        int exponent = Math.max(0, Math.min(attempts, 8));
        Duration delay = Duration.ofSeconds(1L << exponent);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private String failureMessage(RuntimeException error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.isBlank()) {
            return error == null ? "TOOL_COMPLETION_PROJECTION_FAILED" : error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record ProjectionOutcome(
            boolean checkpointSucceeded,
            boolean auditSucceeded,
            boolean failed) {

        public static ProjectionOutcome notClaimed() {
            return new ProjectionOutcome(false, false, false);
        }
    }

    public record ReconciliationOutcome(
            int scanned,
            int checkpointSucceeded,
            int auditSucceeded,
            int failed) {
    }
}
