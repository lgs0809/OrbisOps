package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Durable reservation boundary for externally observable tool calls.
 * Implementations must fence concurrent owners and fail closed on input drift.
 */
public interface ToolExecutionIdempotencyPort {

    Reservation reserve(ReserveCommand command);

    void complete(CompleteCommand command);

    void fail(FailCommand command);

    /** True when this run has a dispatched side effect whose authoritative result is still unknown. */
    default boolean hasUnresolvedSideEffect(String projectId, String runId) {
        return false;
    }

    default List<UnresolvedSideEffect> unresolvedSideEffects(String projectId, String runId) {
        return List.of();
    }

    default List<UnresolvedSideEffect> unresolvedSideEffects(int limit) {
        return List.of();
    }

    default void resolveSideEffect(ResolveSideEffectCommand command) {
        throw new UnsupportedOperationException("TOOL_EXECUTION_SIDE_EFFECT_RECONCILIATION_UNAVAILABLE");
    }

    default List<ProjectionDelivery> pendingProjections(
            Instant dueAt,
            int limit) {
        return List.of();
    }

    default Optional<ProjectionDelivery> claimProjection(
            ProjectionClaimCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_CLAIM_REQUIRED");
        }
        CompletionProjection projection = command.projection();
        return Optional.of(new ProjectionDelivery(
                projection,
                !projection.checkpointType().isBlank(),
                true,
                0,
                command.ownerToken(),
                0L,
                command.leaseExpiresAt()));
    }

    default List<ProjectionDelivery> claimProjections(
            ProjectionBatchClaimCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_BATCH_CLAIM_REQUIRED");
        }
        return pendingProjections(command.claimedAt(), command.limit());
    }

    default void projectionSucceeded(ProjectionSucceededCommand command) {
        // Compatibility no-op for non-persistent implementations.
    }

    default void projectionFailed(ProjectionFailedCommand command) {
        // Compatibility no-op for non-persistent implementations.
    }

    default void releaseProjection(ProjectionReleaseCommand command) {
        // Compatibility no-op for non-persistent implementations.
    }

    static ToolExecutionIdempotencyPort disabled() {
        return new ToolExecutionIdempotencyPort() {
            @Override
            public Reservation reserve(ReserveCommand command) {
                return Reservation.execute(0L);
            }

            @Override
            public void complete(CompleteCommand command) {
                // Compatibility path for callers that do not provide an idempotency key.
            }

            @Override
            public void fail(FailCommand command) {
                // Compatibility path for callers that do not provide an idempotency key.
            }
        };
    }

    enum Disposition {
        EXECUTE,
        REUSE,
        IN_PROGRESS,
        CONFLICT,
        REVIEW_REQUIRED
    }

    enum ProjectionChannel {
        CHECKPOINT,
        AUDIT
    }

    enum SideEffectResolution {
        CONFIRMED_SUCCEEDED,
        CONFIRMED_NOT_EXECUTED
    }

    record UnresolvedSideEffect(
            String idempotencyKey,
            String projectId,
            String runId,
            String nodeId,
            String inputHash,
            String targetHash,
            Map<String, Object> reconciliationContext,
            String errorCode,
            String errorMessage,
            Instant updatedAt) {
        public UnresolvedSideEffect(
                String idempotencyKey,
                String projectId,
                String runId,
                String nodeId,
                String inputHash,
                String targetHash,
                String errorCode,
                String errorMessage,
                Instant updatedAt) {
            this(idempotencyKey, projectId, runId, nodeId, inputHash, targetHash,
                    Map.of(), errorCode, errorMessage, updatedAt);
        }

        public UnresolvedSideEffect {
            idempotencyKey = required(idempotencyKey, "TOOL_IDEMPOTENCY_KEY_REQUIRED");
            projectId = required(projectId, "TOOL_EXECUTION_PROJECT_REQUIRED");
            runId = required(runId, "TOOL_EXECUTION_RUN_REQUIRED");
            nodeId = value(nodeId);
            inputHash = hash(inputHash, "TOOL_IDEMPOTENCY_INPUT_HASH_INVALID");
            targetHash = hash(targetHash, "TOOL_IDEMPOTENCY_TARGET_HASH_INVALID");
            reconciliationContext = immutable(reconciliationContext);
            errorCode = value(errorCode);
            errorMessage = value(errorMessage);
            if (updatedAt == null) throw new IllegalArgumentException("TOOL_EXECUTION_UPDATED_AT_REQUIRED");
        }
    }

    record ResolveSideEffectCommand(
            String idempotencyKey,
            String projectId,
            String runId,
            SideEffectResolution resolution,
            String evidenceId,
            String evidenceHash,
            String note,
            String actor,
            Instant resolvedAt) {
        public ResolveSideEffectCommand {
            idempotencyKey = required(idempotencyKey, "TOOL_IDEMPOTENCY_KEY_REQUIRED");
            projectId = required(projectId, "TOOL_EXECUTION_PROJECT_REQUIRED");
            runId = required(runId, "TOOL_EXECUTION_RUN_REQUIRED");
            if (resolution == null) throw new IllegalArgumentException("TOOL_EXECUTION_RESOLUTION_REQUIRED");
            evidenceId = required(evidenceId, "TOOL_EXECUTION_RECONCILIATION_EVIDENCE_REQUIRED");
            evidenceHash = hash(evidenceHash, "TOOL_EXECUTION_RECONCILIATION_EVIDENCE_HASH_INVALID");
            note = required(note, "TOOL_EXECUTION_RECONCILIATION_NOTE_REQUIRED");
            actor = required(actor, "TOOL_EXECUTION_RECONCILIATION_ACTOR_REQUIRED");
            if (resolvedAt == null) throw new IllegalArgumentException("TOOL_EXECUTION_RECONCILIATION_TIME_REQUIRED");
        }
    }

    record ReserveCommand(
            String idempotencyKey,
            String projectId,
            String runId,
            String nodeId,
            int attempt,
            int toolCallIndex,
            String inputHash,
            String targetHash,
            boolean sideEffecting,
            Map<String, Object> reconciliationContext,
            String ownerToken,
            Instant reservedAt,
            Instant leaseExpiresAt) {

        public ReserveCommand(
                String idempotencyKey,
                String projectId,
                String runId,
                String nodeId,
                int attempt,
                int toolCallIndex,
                String inputHash,
                String targetHash,
                String ownerToken,
                Instant reservedAt,
                Instant leaseExpiresAt) {
            this(idempotencyKey, projectId, runId, nodeId, attempt, toolCallIndex,
                    inputHash, targetHash, false, Map.of(), ownerToken, reservedAt, leaseExpiresAt);
        }

        public ReserveCommand {
            idempotencyKey = required(idempotencyKey, "TOOL_IDEMPOTENCY_KEY_REQUIRED");
            projectId = value(projectId);
            runId = value(runId);
            nodeId = value(nodeId);
            attempt = Math.max(0, attempt);
            toolCallIndex = Math.max(0, toolCallIndex);
            inputHash = hash(inputHash, "TOOL_IDEMPOTENCY_INPUT_HASH_INVALID");
            targetHash = hash(targetHash, "TOOL_IDEMPOTENCY_TARGET_HASH_INVALID");
            reconciliationContext = immutable(reconciliationContext);
            ownerToken = required(ownerToken, "TOOL_IDEMPOTENCY_OWNER_REQUIRED");
            if (reservedAt == null || leaseExpiresAt == null || !leaseExpiresAt.isAfter(reservedAt)) {
                throw new IllegalArgumentException("TOOL_IDEMPOTENCY_LEASE_INVALID");
            }
        }
    }

    record Reservation(
            Disposition disposition,
            long fencingToken,
            boolean allowed,
            String decision,
            ToolExecutionRecordedResult recorded,
            Map<String, Object> payload,
            String reasonCode) {

        public Reservation {
            if (disposition == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_DISPOSITION_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            decision = value(decision);
            payload = immutable(payload);
            reasonCode = value(reasonCode);
            if (disposition == Disposition.REUSE && recorded == null) {
                throw new IllegalArgumentException("TOOL_IDEMPOTENCY_RECORDED_RESULT_REQUIRED");
            }
        }

        public static Reservation execute(long fencingToken) {
            return new Reservation(Disposition.EXECUTE, fencingToken, false, "", null, Map.of(), "");
        }

        public static Reservation reuse(
                long fencingToken,
                boolean allowed,
                String decision,
                ToolExecutionRecordedResult recorded,
                Map<String, Object> payload) {
            return new Reservation(
                    Disposition.REUSE,
                    fencingToken,
                    allowed,
                    required(decision, "TOOL_IDEMPOTENCY_DECISION_REQUIRED"),
                    recorded,
                    payload,
                    "TOOL_EXECUTION_IDEMPOTENT_REUSE");
        }

        public static Reservation rejected(Disposition disposition, String reasonCode) {
            if (disposition == Disposition.EXECUTE || disposition == Disposition.REUSE) {
                throw new IllegalArgumentException("TOOL_IDEMPOTENCY_REJECTION_DISPOSITION_INVALID");
            }
            return new Reservation(disposition, 0L, false, "", null, Map.of(), reasonCode);
        }
    }

    record CompleteCommand(
            String idempotencyKey,
            String ownerToken,
            long fencingToken,
            boolean allowed,
            String decision,
            ToolExecutionRecordedResult recorded,
            Map<String, Object> payload,
            CompletionProjection projection,
            Instant completedAt) {

        public CompleteCommand(
                String idempotencyKey,
                String ownerToken,
                long fencingToken,
                boolean allowed,
                String decision,
                ToolExecutionRecordedResult recorded,
                Map<String, Object> payload,
                Instant completedAt) {
            this(
                    idempotencyKey,
                    ownerToken,
                    fencingToken,
                    allowed,
                    decision,
                    recorded,
                    payload,
                    null,
                    completedAt);
        }

        public CompleteCommand {
            idempotencyKey = required(idempotencyKey, "TOOL_IDEMPOTENCY_KEY_REQUIRED");
            ownerToken = required(ownerToken, "TOOL_IDEMPOTENCY_OWNER_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            decision = required(decision, "TOOL_IDEMPOTENCY_DECISION_REQUIRED");
            if (recorded == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_RECORDED_RESULT_REQUIRED");
            payload = immutable(payload);
            if (completedAt == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_COMPLETED_AT_REQUIRED");
        }
    }

    record CompletionProjection(
            String projectionId,
            String projectId,
            String userId,
            String actor,
            String toolsetId,
            String toolName,
            String executionScope,
            String sessionId,
            String runId,
            Map<String, Object> requestContext,
            String checkpointType,
            Map<String, Object> checkpointPayload,
            ToolExecutionAuditPort.ToolExecutionAuditEvent auditEvent) {

        public CompletionProjection {
            projectionId = required(projectionId, "TOOL_COMPLETION_PROJECTION_ID_REQUIRED");
            projectId = value(projectId);
            userId = value(userId);
            actor = value(actor);
            toolsetId = required(toolsetId, "TOOL_COMPLETION_PROJECTION_TOOLSET_REQUIRED");
            toolName = required(toolName, "TOOL_COMPLETION_PROJECTION_TOOL_REQUIRED");
            executionScope = required(executionScope, "TOOL_COMPLETION_PROJECTION_SCOPE_REQUIRED");
            sessionId = value(sessionId);
            runId = value(runId);
            requestContext = immutable(requestContext);
            checkpointType = value(checkpointType);
            checkpointPayload = immutable(checkpointPayload);
            if (auditEvent == null) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_AUDIT_REQUIRED");
            }
        }

        public ToolExecutionRequest request() {
            ToolExecutionScope scope;
            try {
                scope = ToolExecutionScope.valueOf(executionScope);
            } catch (RuntimeException error) {
                throw new IllegalStateException("TOOL_COMPLETION_PROJECTION_SCOPE_INVALID", error);
            }
            return new ToolExecutionRequest(
                    projectId,
                    userId,
                    actor,
                    toolsetId,
                    toolName,
                    scope,
                    Map.of(),
                    sessionId,
                    runId,
                    requestContext,
                    Map.of());
        }
    }

    record ProjectionClaimCommand(
            CompletionProjection projection,
            String ownerToken,
            Instant claimedAt,
            Instant leaseExpiresAt) {

        public ProjectionClaimCommand {
            if (projection == null) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_REQUIRED");
            }
            ownerToken = required(ownerToken, "TOOL_COMPLETION_PROJECTION_OWNER_REQUIRED");
            if (claimedAt == null || leaseExpiresAt == null || !leaseExpiresAt.isAfter(claimedAt)) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_LEASE_INVALID");
            }
        }
    }

    record ProjectionBatchClaimCommand(
            String ownerToken,
            Instant claimedAt,
            Instant leaseExpiresAt,
            int limit) {

        public ProjectionBatchClaimCommand {
            ownerToken = required(ownerToken, "TOOL_COMPLETION_PROJECTION_OWNER_REQUIRED");
            if (claimedAt == null || leaseExpiresAt == null || !leaseExpiresAt.isAfter(claimedAt)) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_LEASE_INVALID");
            }
            if (limit <= 0 || limit > 200) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_LIMIT_INVALID");
            }
        }
    }

    record ProjectionDelivery(
            CompletionProjection projection,
            boolean checkpointPending,
            boolean auditPending,
            int attempts,
            String ownerToken,
            long fencingToken,
            Instant leaseExpiresAt) {

        public ProjectionDelivery(
                CompletionProjection projection,
                boolean checkpointPending,
                boolean auditPending,
                int attempts) {
            this(projection, checkpointPending, auditPending, attempts, "", 0L, null);
        }

        public ProjectionDelivery {
            if (projection == null) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_REQUIRED");
            }
            attempts = Math.max(0, attempts);
            ownerToken = value(ownerToken);
            fencingToken = Math.max(0L, fencingToken);
            if (!ownerToken.isBlank() && fencingToken > 0L && leaseExpiresAt == null) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_LEASE_REQUIRED");
            }
        }

        public boolean claimed() {
            return !ownerToken.isBlank() && fencingToken > 0L;
        }
    }

    record ProjectionSucceededCommand(
            String projectionId,
            ProjectionChannel channel,
            String ownerToken,
            long fencingToken,
            Instant completedAt) {

        public ProjectionSucceededCommand(
                String projectionId,
                ProjectionChannel channel,
                Instant completedAt) {
            this(projectionId, channel, "", 0L, completedAt);
        }

        public ProjectionSucceededCommand {
            projectionId = required(projectionId, "TOOL_COMPLETION_PROJECTION_ID_REQUIRED");
            ownerToken = value(ownerToken);
            fencingToken = Math.max(0L, fencingToken);
            if (channel == null || completedAt == null) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_SUCCESS_INVALID");
            }
        }
    }

    record ProjectionFailedCommand(
            String projectionId,
            ProjectionChannel channel,
            String ownerToken,
            long fencingToken,
            String errorMessage,
            Instant failedAt,
            Instant retryAt) {

        public ProjectionFailedCommand(
                String projectionId,
                ProjectionChannel channel,
                String errorMessage,
                Instant failedAt,
                Instant retryAt) {
            this(projectionId, channel, "", 0L, errorMessage, failedAt, retryAt);
        }

        public ProjectionFailedCommand {
            projectionId = required(projectionId, "TOOL_COMPLETION_PROJECTION_ID_REQUIRED");
            ownerToken = value(ownerToken);
            fencingToken = Math.max(0L, fencingToken);
            if (channel == null || failedAt == null || retryAt == null || retryAt.isBefore(failedAt)) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_FAILURE_INVALID");
            }
            errorMessage = value(errorMessage);
        }
    }

    record ProjectionReleaseCommand(
            String projectionId,
            String ownerToken,
            long fencingToken,
            Instant releasedAt) {

        public ProjectionReleaseCommand {
            projectionId = required(projectionId, "TOOL_COMPLETION_PROJECTION_ID_REQUIRED");
            ownerToken = required(ownerToken, "TOOL_COMPLETION_PROJECTION_OWNER_REQUIRED");
            if (fencingToken <= 0L || releasedAt == null) {
                throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_RELEASE_INVALID");
            }
        }
    }

    record FailCommand(
            String idempotencyKey,
            String ownerToken,
            long fencingToken,
            String errorCode,
            String errorMessage,
            boolean uncertainSideEffect,
            Instant failedAt) {

        public FailCommand {
            idempotencyKey = required(idempotencyKey, "TOOL_IDEMPOTENCY_KEY_REQUIRED");
            ownerToken = required(ownerToken, "TOOL_IDEMPOTENCY_OWNER_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            errorCode = required(errorCode, "TOOL_IDEMPOTENCY_ERROR_CODE_REQUIRED");
            errorMessage = value(errorMessage);
            if (failedAt == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_FAILED_AT_REQUIRED");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String hash(String value, String error) {
        String normalized = value(value).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
