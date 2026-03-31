package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocation;
import cn.lgs.orbisops.domain.toolexecution.service.ToolExecutionPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class ToolExecutionApplicationService {

    private static final Duration IDEMPOTENCY_LEASE = Duration.ofMinutes(2);

    private final ToolExecutionCatalogPort catalog;
    private final ToolExecutionDispatchPort dispatch;
    private final ToolExecutionRecordPort records;
    private final ToolExecutionCheckpointPort checkpoints;
    private final ToolExecutionAuditPort audit;
    private final Supplier<String> toolCallIdSupplier;
    private final LongSupplier nanoTimeSupplier;
    private final ToolExecutionPolicy policy;
    private final ToolExecutionIdempotencyPort idempotency;
    private final ToolExecutionTransactionPort transactions;
    private final ToolExecutionCompletionReconciliationService completionReconciliation;
    private final ToolExecutionEmergencyStopPort emergencyStop;
    private final Clock clock;

    public ToolExecutionApplicationService(
            ToolExecutionCatalogPort catalog,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            Supplier<String> toolCallIdSupplier,
            LongSupplier nanoTimeSupplier) {
        this(catalog, dispatch, records, checkpoints, audit,
                toolCallIdSupplier, nanoTimeSupplier, new ToolExecutionPolicy(),
                ToolExecutionIdempotencyPort.disabled(),
                ToolExecutionTransactionPort.direct(),
                ToolExecutionEmergencyStopPort.disabled(),
                Clock.systemUTC());
    }

    public ToolExecutionApplicationService(
            ToolExecutionCatalogPort catalog,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            ToolExecutionIdempotencyPort idempotency,
            Supplier<String> toolCallIdSupplier,
            LongSupplier nanoTimeSupplier,
            Clock clock) {
        this(catalog, dispatch, records, checkpoints, audit,
                toolCallIdSupplier, nanoTimeSupplier, new ToolExecutionPolicy(),
                idempotency, ToolExecutionTransactionPort.direct(),
                ToolExecutionEmergencyStopPort.disabled(), clock);
    }

    public ToolExecutionApplicationService(
            ToolExecutionCatalogPort catalog,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            ToolExecutionIdempotencyPort idempotency,
            ToolExecutionTransactionPort transactions,
            Supplier<String> toolCallIdSupplier,
            LongSupplier nanoTimeSupplier,
            Clock clock) {
        this(catalog, dispatch, records, checkpoints, audit,
                toolCallIdSupplier, nanoTimeSupplier, new ToolExecutionPolicy(),
                idempotency, transactions, ToolExecutionEmergencyStopPort.disabled(), clock);
    }

    public ToolExecutionApplicationService(
            ToolExecutionCatalogPort catalog,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            ToolExecutionIdempotencyPort idempotency,
            ToolExecutionTransactionPort transactions,
            ToolExecutionEmergencyStopPort emergencyStop,
            Supplier<String> toolCallIdSupplier,
            LongSupplier nanoTimeSupplier,
            Clock clock) {
        this(catalog, dispatch, records, checkpoints, audit,
                toolCallIdSupplier, nanoTimeSupplier, new ToolExecutionPolicy(),
                idempotency, transactions, emergencyStop, clock);
    }

    ToolExecutionApplicationService(
            ToolExecutionCatalogPort catalog,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            Supplier<String> toolCallIdSupplier,
            LongSupplier nanoTimeSupplier,
            ToolExecutionPolicy policy,
            ToolExecutionIdempotencyPort idempotency,
            ToolExecutionTransactionPort transactions,
            ToolExecutionEmergencyStopPort emergencyStop,
            Clock clock) {
        if (catalog == null) throw new IllegalArgumentException("TOOL_EXECUTION_CATALOG_PORT_REQUIRED");
        if (dispatch == null) throw new IllegalArgumentException("TOOL_EXECUTION_DISPATCH_PORT_REQUIRED");
        if (records == null) throw new IllegalArgumentException("TOOL_EXECUTION_RECORD_PORT_REQUIRED");
        if (checkpoints == null) throw new IllegalArgumentException("TOOL_EXECUTION_CHECKPOINT_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("TOOL_EXECUTION_AUDIT_PORT_REQUIRED");
        if (toolCallIdSupplier == null) throw new IllegalArgumentException("TOOL_EXECUTION_CALL_ID_SUPPLIER_REQUIRED");
        if (nanoTimeSupplier == null) throw new IllegalArgumentException("TOOL_EXECUTION_NANO_TIME_SUPPLIER_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("TOOL_EXECUTION_POLICY_REQUIRED");
        if (idempotency == null) throw new IllegalArgumentException("TOOL_EXECUTION_IDEMPOTENCY_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("TOOL_EXECUTION_TRANSACTION_PORT_REQUIRED");
        if (emergencyStop == null) throw new IllegalArgumentException("TOOL_EXECUTION_EMERGENCY_STOP_PORT_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("TOOL_EXECUTION_CLOCK_REQUIRED");
        this.catalog = catalog;
        this.dispatch = dispatch;
        this.records = records;
        this.checkpoints = checkpoints;
        this.audit = audit;
        this.toolCallIdSupplier = toolCallIdSupplier;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.policy = policy;
        this.idempotency = idempotency;
        this.transactions = transactions;
        this.completionReconciliation = new ToolExecutionCompletionReconciliationService(
                idempotency, checkpoints, audit, clock);
        this.emergencyStop = emergencyStop;
        this.clock = clock;
    }

    public ToolExecutionResult execute(ToolInvocation invocation) {
        if (invocation == null) throw new IllegalArgumentException("TOOL_INVOCATION_REQUIRED");
        ToolExecutionRequest request = ToolExecutionRequest.from(invocation);
        return execute(request).result(invocation.context().projectId());
    }

    public java.util.List<ToolExecutionIdempotencyPort.UnresolvedSideEffect> unresolvedSideEffects(
            String projectId,
            String runId) {
        return idempotency.unresolvedSideEffects(projectId, runId);
    }

    public java.util.List<ToolExecutionIdempotencyPort.UnresolvedSideEffect> unresolvedSideEffects(int limit) {
        return idempotency.unresolvedSideEffects(limit);
    }

    public void resolveUncertainSideEffect(ToolExecutionIdempotencyPort.ResolveSideEffectCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_EXECUTION_SIDE_EFFECT_RESOLUTION_REQUIRED");
        }
        idempotency.resolveSideEffect(command);
        audit.record(new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                command.projectId(),
                "tool_side_effect_reconciled",
                command.idempotencyKey(),
                Map.of(
                        "runId", command.runId(),
                        "resolution", command.resolution().name(),
                        "evidenceId", command.evidenceId(),
                        "evidenceHash", command.evidenceHash(),
                        "note", command.note(),
                        "actor", command.actor(),
                        "resolvedAt", command.resolvedAt().toString())));
    }

    public ToolExecutionResponse execute(ToolExecutionRequest request) {
        if (request == null) throw new IllegalArgumentException("TOOL_EXECUTION_REQUEST_REQUIRED");
        assertLandingRunReconciled(request);
        long startedNanos = nanoTimeSupplier.getAsLong();
        ToolExecutionResolution resolution = catalog.resolve(request);
        ToolExecutionTarget target = resolution.target();
        ToolExecutionDecision decision = policy.restrictToReadOnly(request, target, resolution.decision());
        String targetId = target.toolsetId() + "/" + target.toolName();
        String toolCallId = toolCallIdSupplier.get();
        // A previous receipt cannot override current authority or a read-only restriction.
        // Denials receive their own audit record without altering the original execution ledger.
        String idempotencyKey = decision.allowed() ? effectiveIdempotencyKey(
                request,
                target,
                text(request.requestContext().get("idempotencyKey"))) : "";
        String inputHash = CanonicalObjectHasher.sha256(request.arguments());
        String targetHash = CanonicalObjectHasher.sha256(Map.of(
                "toolsetId", target.toolsetId(),
                "toolName", target.toolName(),
                "provider", target.providerDescriptor(),
                "scope", request.scope().name()));
        if (decision.allowed() && !target.readOnly() && emergencyStop.blocks(request, target)) {
            return emergencyBlocked(request, target, targetId, toolCallId, startedNanos);
        }
        ToolExecutionIdempotencyPort.Reservation reservation = reserve(
                request, target, decision.allowed() && !target.readOnly(),
                idempotencyKey, inputHash, targetHash, toolCallId);
        if (reservation != null
                && reservation.disposition() == ToolExecutionIdempotencyPort.Disposition.REUSE) {
            bestEffortReuseProjection(
                    request, targetId, decision, idempotencyKey, reservation.recorded());
            return new ToolExecutionResponse(
                    reservation.allowed(),
                    reservation.decision(),
                    request.scope(),
                    target,
                    reservation.recorded(),
                    reservation.payload());
        }

        if (!decision.allowed()) {
            boolean authorityCompleted = false;
            try {
                Map<String, Object> output = policy.blockedPayload(target, decision);
                AuthoritativeCompletion completion = transactions.required(() -> {
                    ToolExecutionRecordedResult recorded = record(
                            request, target, "TOOL_BLOCKED", output,
                            elapsedMillis(startedNanos), false);
                    ToolExecutionResponse response = new ToolExecutionResponse(
                            false, decision.decision(), request.scope(), target, recorded, output);
                    ToolExecutionIdempotencyPort.CompletionProjection projection = completionProjection(
                            request,
                            idempotencyKey,
                            toolCallId,
                            targetId,
                            "blocked",
                            "",
                            Map.of(),
                            auditPayload(request, decision, recorded, null));
                    complete(idempotencyKey, toolCallId, reservation, response, projection);
                    return new AuthoritativeCompletion(response, projection);
                });
                authorityCompleted = true;
                projectCompletion(idempotencyKey, completion.projection());
                return completion.response();
            } catch (RuntimeException error) {
                if (!authorityCompleted) {
                    attemptFail(
                            idempotencyKey, toolCallId, reservation,
                            error, false);
                }
                throw error;
            }
        }

        checkpoints.checkpoint(
                request,
                "TOOL_EXECUTION_STARTED",
                policy.startedCheckpoint(
                        toolCallId, target, request,
                        inputHash, decision));
        boolean dispatchStarted = false;
        boolean authorityCompleted = false;
        try {
            dispatchStarted = true;
            Object output = dispatch.dispatch(
                    target,
                    dispatchRequest(request, reservation, idempotencyKey, toolCallId));
            long durationMs = elapsedMillis(startedNanos);
            String source = policy.source(request);
            AuthoritativeCompletion completion = transactions.required(() -> {
                ToolExecutionRecordedResult recorded = record(
                        request, target, source, output, durationMs, true);
                ToolExecutionResponse response = new ToolExecutionResponse(
                        true, "ALLOWED", request.scope(), target, recorded,
                        policy.responsePayload(output, 2000));
                ToolExecutionIdempotencyPort.CompletionProjection projection = completionProjection(
                        request,
                        idempotencyKey,
                        toolCallId,
                        targetId,
                        "allowed",
                        "TOOL_EXECUTION_COMPLETED",
                        policy.completedCheckpoint(
                                toolCallId, target, recorded.resultId(), recorded.outputHash()),
                        auditPayload(request, decision, recorded, null));
                complete(idempotencyKey, toolCallId, reservation, response, projection);
                return new AuthoritativeCompletion(response, projection);
            });
            authorityCompleted = true;
            projectCompletion(idempotencyKey, completion.projection());
            return completion.response();
        } catch (RuntimeException error) {
            if (!authorityCompleted) {
                attemptFail(
                        idempotencyKey, toolCallId, reservation,
                        error, dispatchStarted && !(error instanceof
                                cn.lgs.orbisops.domain.toolexecution.model.ToolDispatchFailureEvidence evidence
                                && !evidence.dispatched()));
            }
            try {
                checkpoints.checkpoint(
                        request,
                        "TOOL_EXECUTION_FAILED",
                        policy.failedCheckpoint(toolCallId, target, error));
            } catch (RuntimeException checkpointFailure) {
                error.addSuppressed(checkpointFailure);
            }
            try {
                audit.record(new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        request.projectId(), "failed", targetId,
                        auditPayload(request, decision, null, error)));
            } catch (RuntimeException auditFailure) {
                error.addSuppressed(auditFailure);
            }
            throw error;
        }
    }

    private ToolExecutionResponse emergencyBlocked(
            ToolExecutionRequest request,
            ToolExecutionTarget target,
            String targetId,
            String toolCallId,
            long startedNanos) {
        ToolExecutionDecision decision = new ToolExecutionDecision(
                false,
                "BLOCKED",
                "TOOL_EXECUTION_EMERGENCY_STOP_ACTIVE",
                "Emergency Stop is active; new side effects are blocked.",
                target.riskLevel(),
                Map.of("projectId", request.projectId()));
        Map<String, Object> output = policy.blockedPayload(target, decision);
        AuthoritativeCompletion completion = transactions.required(() -> {
            ToolExecutionRecordedResult recorded = record(
                    request, target, "TOOL_BLOCKED", output,
                    elapsedMillis(startedNanos), false);
            ToolExecutionResponse response = new ToolExecutionResponse(
                    false, decision.decision(), request.scope(), target, recorded, output);
            return new AuthoritativeCompletion(response, completionProjection(
                    request,
                    "",
                    toolCallId,
                    targetId,
                    "emergency_stop_blocked",
                    "TOOL_EXECUTION_BLOCKED",
                    Map.of(
                            "toolCallId", toolCallId,
                            "reasonCode", decision.reasonCode(),
                            "toolsetId", target.toolsetId(),
                            "toolName", target.toolName()),
                    auditPayload(request, decision, recorded, null)));
        });
        projectCompletion("", completion.projection());
        return completion.response();
    }

    private ToolExecutionIdempotencyPort.Reservation reserve(
            ToolExecutionRequest request,
            ToolExecutionTarget target,
            boolean sideEffecting,
            String idempotencyKey,
            String inputHash,
            String targetHash,
            String ownerToken) {
        if (idempotencyKey.isBlank()) return null;
        Instant now = clock.instant();
        ToolExecutionIdempotencyPort.Reservation reservation = idempotency.reserve(
                new ToolExecutionIdempotencyPort.ReserveCommand(
                        idempotencyKey,
                        request.projectId(),
                        request.runId(),
                        firstText(
                                request.requestContext().get("workflowNodeId"),
                                request.requestContext().get("nodeId")),
                        integer(request.requestContext().get("workflowAttempt")),
                        integer(request.requestContext().get("workflowToolCallIndex")),
                        inputHash,
                        targetHash,
                        sideEffecting,
                        reconciliationContext(request, target),
                        ownerToken,
                        now,
                        now.plus(IDEMPOTENCY_LEASE)));
        return switch (reservation.disposition()) {
            case EXECUTE, REUSE -> reservation;
            case CONFLICT -> throw new SecurityException(
                    reason(reservation, "TOOL_EXECUTION_IDEMPOTENCY_CONFLICT"));
            case IN_PROGRESS -> throw new IllegalStateException(
                    reason(reservation, "TOOL_EXECUTION_ALREADY_RUNNING"));
            case REVIEW_REQUIRED -> throw new IllegalStateException(
                    reason(reservation, "TOOL_EXECUTION_REVIEW_REQUIRED"));
        };
    }

    private void complete(
            String idempotencyKey,
            String ownerToken,
            ToolExecutionIdempotencyPort.Reservation reservation,
            ToolExecutionResponse response,
            ToolExecutionIdempotencyPort.CompletionProjection projection) {
        if (idempotencyKey.isBlank() || reservation == null) return;
        idempotency.complete(new ToolExecutionIdempotencyPort.CompleteCommand(
                idempotencyKey,
                ownerToken,
                reservation.fencingToken(),
                response.allowed(),
                response.decision(),
                response.recorded(),
                response.payload(),
                projection,
                clock.instant()));
    }

    private ToolExecutionIdempotencyPort.CompletionProjection completionProjection(
            ToolExecutionRequest request,
            String idempotencyKey,
            String toolCallId,
            String targetId,
            String action,
            String checkpointType,
            Map<String, Object> checkpointPayload,
            Map<String, Object> auditPayload) {
        String projectionId = "tool-completion-" + CanonicalObjectHasher.sha256(Map.of(
                "idempotencyKey", idempotencyKey.isBlank() ? toolCallId : idempotencyKey,
                "projectId", request.projectId(),
                "runId", request.runId(),
                "targetId", targetId,
                "action", action)).substring(0, 32);
        return new ToolExecutionIdempotencyPort.CompletionProjection(
                projectionId,
                request.projectId(),
                request.userId(),
                request.actor(),
                request.toolsetId(),
                request.toolName(),
                request.scope().name(),
                request.sessionId(),
                request.runId(),
                projectionRequestContext(request),
                checkpointType,
                withProjectionId(checkpointPayload, projectionId),
                new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        request.projectId(),
                        action,
                        targetId,
                        withProjectionId(auditPayload, projectionId)));
    }

    private void projectCompletion(
            String idempotencyKey,
            ToolExecutionIdempotencyPort.CompletionProjection projection) {
        if (idempotencyKey.isBlank()) {
            if (!projection.checkpointType().isBlank()) {
                checkpoints.checkpoint(
                        projection.request(),
                        projection.checkpointType(),
                        projection.checkpointPayload());
            }
            audit.record(projection.auditEvent());
            return;
        }
        completionReconciliation.project(projection);
    }

    private void bestEffortReuseProjection(
            ToolExecutionRequest request,
            String targetId,
            ToolExecutionDecision decision,
            String idempotencyKey,
            ToolExecutionRecordedResult recorded) {
        try {
            checkpoints.checkpoint(
                    request,
                    "TOOL_EXECUTION_REUSED",
                    Map.of(
                            "idempotencyKey", idempotencyKey,
                            "resultId", recorded.resultId(),
                            "outputHash", recorded.outputHash()));
        } catch (RuntimeException ignored) {
            // The authoritative completion projection is reconciled separately.
        }
        try {
            audit.record(new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                    request.projectId(), "reused", targetId,
                    auditPayload(request, decision, recorded, null)));
        } catch (RuntimeException ignored) {
            // Reuse telemetry must not turn an authoritative success into a failure.
        }
    }

    private ToolExecutionRequest dispatchRequest(
            ToolExecutionRequest request,
            ToolExecutionIdempotencyPort.Reservation reservation,
            String idempotencyKey,
            String toolCallId) {
        Map<String, Object> context = new LinkedHashMap<>(request.requestContext());
        if (!idempotencyKey.isBlank()) context.put("idempotencyKey", idempotencyKey);
        if (!toolCallId.isBlank()) context.put("toolCallId", toolCallId);
        if (reservation != null) context.put("fencingToken", reservation.fencingToken());
        return new ToolExecutionRequest(
                request.projectId(),
                request.userId(),
                request.actor(),
                request.toolsetId(),
                request.toolName(),
                request.scope(),
                request.arguments(),
                request.sessionId(),
                request.runId(),
                context,
                request.landingContext());
    }

    private Map<String, Object> projectionRequestContext(
            ToolExecutionRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        copyContextValue(request.requestContext(), context, "metadata");
        copyContextValue(request.requestContext(), context, "idempotencyKey");
        copyContextValue(request.requestContext(), context, "workflowNodeId");
        copyContextValue(request.requestContext(), context, "workflowAttempt");
        copyContextValue(request.requestContext(), context, "workflowToolCallIndex");
        return context;
    }

    private void copyContextValue(
            Map<String, Object> source,
            Map<String, Object> target,
            String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> withProjectionId(
            Map<String, Object> payload,
            String projectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (payload != null) result.putAll(payload);
        result.put("projectionId", projectionId);
        return result;
    }

    private void attemptFail(
            String idempotencyKey,
            String ownerToken,
            ToolExecutionIdempotencyPort.Reservation reservation,
            RuntimeException primary,
            boolean uncertainSideEffect) {
        try {
            fail(idempotencyKey, ownerToken, reservation, primary, uncertainSideEffect);
        } catch (RuntimeException ledgerFailure) {
            primary.addSuppressed(ledgerFailure);
        }
    }

    private void fail(
            String idempotencyKey,
            String ownerToken,
            ToolExecutionIdempotencyPort.Reservation reservation,
            RuntimeException error,
            boolean uncertainSideEffect) {
        if (idempotencyKey.isBlank() || reservation == null) return;
        idempotency.fail(new ToolExecutionIdempotencyPort.FailCommand(
                idempotencyKey,
                ownerToken,
                reservation.fencingToken(),
                "TOOL_EXECUTION_FAILED",
                policy.failureMessage(error),
                uncertainSideEffect,
                clock.instant()));
    }

    private ToolExecutionRecordedResult record(
            ToolExecutionRequest request,
            ToolExecutionTarget target,
            String source,
            Object output,
            long durationMs,
            boolean verified) {
        return records.record(new ToolExecutionRecordPort.ToolExecutionRecordCommand(
                request,
                target,
                source,
                policy.evidenceSourceType(source),
                policy.outputStatus(output, source),
                output,
                durationMs,
                verified && policy.verifiedEvidence(source)));
    }

    private Map<String, Object> auditPayload(
            ToolExecutionRequest request,
            ToolExecutionDecision decision,
            ToolExecutionRecordedResult recorded,
            RuntimeException error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionScope", request.scope().name());
        payload.put("runId", request.runId());
        payload.put("decision", decision.decision());
        payload.put("reasonCode", decision.reasonCode());
        String idempotencyKey = text(request.requestContext().get("idempotencyKey"));
        if (!idempotencyKey.isBlank()) payload.put("idempotencyKey", idempotencyKey);
        if (recorded != null) payload.put("resultId", recorded.resultId());
        if (error != null) payload.put("error", policy.failureMessage(error));
        return payload;
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (nanoTimeSupplier.getAsLong() - startedNanos) / 1_000_000L);
    }

    private int integer(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        try {
            return Math.max(0, Integer.parseInt(text(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String reason(
            ToolExecutionIdempotencyPort.Reservation reservation,
            String fallback) {
        return reservation.reasonCode() == null || reservation.reasonCode().isBlank()
                ? fallback
                : reservation.reasonCode();
    }

    private String effectiveIdempotencyKey(
            ToolExecutionRequest request,
            ToolExecutionTarget target,
            String providedKey) {
        if (!providedKey.isBlank()) return providedKey;
        if (!ToolExecutionScope.APPROVED_LANDING.equals(request.scope()) || target.readOnly()) return "";
        Map<String, Object> landing = request.landingContext();
        return "landing:auto:" + CanonicalObjectHasher.sha256(Map.of(
                "projectId", text(request.projectId()),
                "changePackageId", text(landing.get("changePackageId")),
                "approvedPackageHash", text(landing.get("approvedPackageHash")),
                "approvedPackageVersion", text(landing.get("approvedPackageVersion")),
                "toolsetId", target.toolsetId(),
                "toolName", target.toolName(),
                "arguments", request.arguments()));
    }

    private Map<String, Object> reconciliationContext(
            ToolExecutionRequest request,
            ToolExecutionTarget target) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("adapterType", target.adapterType());
        context.put("toolsetId", target.toolsetId());
        context.put("toolName", target.toolName());
        context.put("providerType", target.providerDescriptor().providerType().name());
        context.put("providerId", target.providerDescriptor().providerId());
        context.put("mcpServerId", target.providerDescriptor().mcpServerId());
        context.put("remoteToolName", target.providerDescriptor().remoteToolName());
        context.put("actor", request.actor());
        context.put("executionScope", request.scope().name());
        if (!request.landingContext().isEmpty()) {
            putText(context, "changePackageId", request.landingContext().get("changePackageId"));
            putText(context, "approvedPackageHash", request.landingContext().get("approvedPackageHash"));
            putText(context, "approvedPackageVersion", request.landingContext().get("approvedPackageVersion"));
            putText(context, "operationId", request.landingContext().get("operationId"));
        }
        return Map.copyOf(context);
    }

    private void putText(Map<String, Object> target, String key, Object value) {
        String normalized = text(value);
        if (!normalized.isBlank()) target.put(key, normalized);
    }

    private void assertLandingRunReconciled(ToolExecutionRequest request) {
        if (!ToolExecutionScope.APPROVED_LANDING.equals(request.scope())) return;
        if (idempotency.hasUnresolvedSideEffect(request.projectId(), request.runId())) {
            throw new IllegalStateException("TOOL_EXECUTION_RECONCILIATION_REQUIRED");
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record AuthoritativeCompletion(
            ToolExecutionResponse response,
            ToolExecutionIdempotencyPort.CompletionProjection projection) {
    }
}
