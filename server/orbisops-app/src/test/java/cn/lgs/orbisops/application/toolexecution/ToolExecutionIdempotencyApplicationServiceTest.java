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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionIdempotencyApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void completedExecutionMustBeReusedWithoutRedispatchOrRerecord() {
        InMemoryIdempotencyPort idempotency = new InMemoryIdempotencyPort();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger records = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                idempotency,
                (target, request) -> {
                    dispatches.incrementAndGet();
                    return Map.of("status", "SUCCEEDED", "value", 7);
                },
                command -> {
                    records.incrementAndGet();
                    return recorded("tool-result-1", command.durationMs());
                });

        ToolExecutionResponse first = service.execute(request(Map.of("command", "pwd")));
        ToolExecutionResponse second = service.execute(request(Map.of("command", "pwd")));

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertEquals(first.recorded(), second.recorded());
        assertEquals(1, dispatches.get());
        assertEquals(1, records.get());
        assertEquals(1, idempotency.completions.get());
    }

    @Test
    void cachedWriteReceiptCannotSatisfyReadOnlyVerification() {
        InMemoryIdempotencyPort idempotency = new InMemoryIdempotencyPort();
        AtomicInteger dispatches = new AtomicInteger();
        var service = service(idempotency,
                (target, request) -> { dispatches.incrementAndGet(); return Map.of("status", "SUCCEEDED"); },
                command -> recorded("tool-result-1", command.durationMs()));
        var original = request(Map.of("command", "pwd"));
        assertTrue(service.execute(original).allowed());
        var context = new java.util.LinkedHashMap<>(original.requestContext());
        context.put("requireReadOnly", true);
        var restricted = new ToolExecutionRequest(original.projectId(), original.userId(), original.actor(),
                original.toolsetId(), original.toolName(), original.scope(), original.arguments(),
                original.sessionId(), original.runId(), context, original.landingContext());
        var denied = service.execute(restricted);
        assertEquals(false, denied.allowed());
        assertEquals("READ_ONLY_REQUIRED", denied.payload().get("reasonCode"));
        assertEquals(1, dispatches.get());
        assertEquals(1, idempotency.completions.get());
    }

    @Test
    void sameIdempotencyKeyWithDifferentInputMustFailClosed() {
        InMemoryIdempotencyPort idempotency = new InMemoryIdempotencyPort();
        AtomicInteger dispatches = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                idempotency,
                (target, request) -> {
                    dispatches.incrementAndGet();
                    return Map.of("status", "SUCCEEDED");
                },
                command -> recorded("tool-result-1", command.durationMs()));

        service.execute(request(Map.of("command", "pwd")));
        SecurityException error = assertThrows(
                SecurityException.class,
                () -> service.execute(request(Map.of("command", "whoami"))));

        assertEquals("TOOL_EXECUTION_IDEMPOTENCY_CONFLICT", error.getMessage());
        assertEquals(1, dispatches.get());
    }

    @Test
    void landingMutatingToolWithoutCallerKeyGetsStableFrozenPackageIdempotencyKey() {
        InMemoryIdempotencyPort idempotency = new InMemoryIdempotencyPort();
        AtomicInteger dispatches = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                idempotency,
                (target, request) -> {
                    dispatches.incrementAndGet();
                    return Map.of("status", "SUCCEEDED");
                },
                command -> recorded("tool-result-1", command.durationMs()));

        ToolExecutionResponse first = service.execute(landingRequestWithoutIdempotency());
        ToolExecutionResponse second = service.execute(landingRequestWithoutIdempotency());

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertEquals(1, dispatches.get());
        assertTrue(idempotency.reserved.idempotencyKey().startsWith("landing:auto:"));
    }

    @Test
    void landingRunWithPriorUncertainSideEffectBlocksEverySubsequentToolBeforeDispatch() {
        InMemoryIdempotencyPort idempotency = new InMemoryIdempotencyPort();
        idempotency.reviewRequired = true;
        AtomicInteger dispatches = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                idempotency,
                (target, request) -> {
                    dispatches.incrementAndGet();
                    return Map.of("status", "SUCCEEDED");
                },
                command -> recorded("tool-result-1", command.durationMs()));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.execute(landingRequest()));

        assertEquals("TOOL_EXECUTION_RECONCILIATION_REQUIRED", error.getMessage());
        assertEquals(0, dispatches.get());
    }

    @Test
    void uncertainSideEffectMustRequireReviewAndMustNotBeReplayed() {
        InMemoryIdempotencyPort idempotency = new InMemoryIdempotencyPort();
        AtomicInteger dispatches = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                idempotency,
                (target, request) -> {
                    dispatches.incrementAndGet();
                    throw new IllegalStateException("remote result unknown");
                },
                command -> recorded("tool-result-1", command.durationMs()));

        assertThrows(IllegalStateException.class,
                () -> service.execute(request(Map.of("command", "apply"))));
        IllegalStateException retry = assertThrows(
                IllegalStateException.class,
                () -> service.execute(request(Map.of("command", "apply"))));

        assertEquals("TOOL_EXECUTION_REVIEW_REQUIRED", retry.getMessage());
        assertEquals(1, dispatches.get());
        assertEquals(1, idempotency.failures.get());
    }

    @Test
    void onlyTypedPreDispatchEvidenceMayAvoidUnknownSideEffectQuarantine() {
        class ProviderFailure extends IllegalStateException implements
                cn.lgs.orbisops.domain.toolexecution.model.ToolDispatchFailureEvidence {
            private final boolean sent;
            ProviderFailure(boolean sent) { super("provider boundary failure"); this.sent = sent; }
            public boolean dispatched() { return sent; }
        }
        for (boolean sent : new boolean[]{false, true}) {
            var ledger = new InMemoryIdempotencyPort();
            var service = service(ledger, (target, request) -> { throw new ProviderFailure(sent); },
                    command -> recorded("unused", command.durationMs()));
            assertThrows(ProviderFailure.class, () -> service.execute(request(Map.of("command", "apply"))));
            assertEquals(sent, ledger.reviewRequired);
            assertEquals(1, ledger.failures.get());
        }
    }

    private ToolExecutionApplicationService service(
            ToolExecutionIdempotencyPort idempotency,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records) {
        ToolExecutionTarget target = new ToolExecutionTarget(
                "code.repair", "code_bash", "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
        AtomicLong nanos = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        return new ToolExecutionApplicationService(
                request -> new ToolExecutionResolution(
                        target, ToolExecutionDecision.allowed("MEDIUM", Map.of())),
                dispatch,
                records,
                (request, type, payload) -> { },
                event -> { },
                idempotency,
                () -> "tool-call-" + calls.incrementAndGet(),
                () -> nanos.addAndGet(1_000_000L),
                CLOCK);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                arguments,
                "session-1",
                "run-1",
                Map.of(
                        "idempotencyKey", "run-1:node-1:1:0",
                        "workflowNodeId", "node-1",
                        "workflowAttempt", 1,
                        "workflowToolCallIndex", 0),
                Map.of());
    }

    private ToolExecutionRequest landingRequestWithoutIdempotency() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.APPROVED_LANDING,
                Map.of("command", "apply"),
                "session-1",
                "run-2",
                Map.of("nodeId", "landing-react"),
                Map.of(
                        "landingApproved", true,
                        "changePackageId", "cp-1",
                        "approvedPackageHash", "hash-1",
                        "approvedPackageVersion", 1,
                        "internalCaller", "LANDING_RUNTIME",
                        "landingRuntimeToken", "test"));
    }

    private ToolExecutionRequest landingRequest() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.APPROVED_LANDING,
                Map.of("command", "apply"),
                "session-1",
                "run-1",
                Map.of("idempotencyKey", "landing-call-1", "nodeId", "landing-react"),
                Map.of(
                        "landingApproved", true,
                        "changePackageId", "cp-1",
                        "approvedPackageHash", "hash-1",
                        "approvedPackageVersion", 1,
                        "internalCaller", "LANDING_RUNTIME",
                        "landingRuntimeToken", "test"));
    }

    private ToolExecutionRecordedResult recorded(String resultId, long durationMs) {
        return new ToolExecutionRecordedResult(
                resultId, "evidence-1", "preview", "a".repeat(64), false,
                "db:" + resultId, "b".repeat(64), durationMs);
    }

    private static final class InMemoryIdempotencyPort implements ToolExecutionIdempotencyPort {
        private ReserveCommand reserved;
        private CompleteCommand completed;
        private boolean reviewRequired;
        private final AtomicInteger completions = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public Reservation reserve(ReserveCommand command) {
            if (reviewRequired) {
                return Reservation.rejected(
                        Disposition.REVIEW_REQUIRED,
                        "TOOL_EXECUTION_REVIEW_REQUIRED");
            }
            if (reserved == null) {
                reserved = command;
                return Reservation.execute(1L);
            }
            if (!reserved.inputHash().equals(command.inputHash())
                    || !reserved.targetHash().equals(command.targetHash())) {
                return Reservation.rejected(
                        Disposition.CONFLICT,
                        "TOOL_EXECUTION_IDEMPOTENCY_CONFLICT");
            }
            if (completed != null) {
                return Reservation.reuse(
                        completed.fencingToken(),
                        completed.allowed(),
                        completed.decision(),
                        completed.recorded(),
                        completed.payload());
            }
            return Reservation.rejected(
                    Disposition.IN_PROGRESS,
                    "TOOL_EXECUTION_ALREADY_RUNNING");
        }

        @Override
        public boolean hasUnresolvedSideEffect(String projectId, String runId) {
            return reviewRequired;
        }

        @Override
        public void complete(CompleteCommand command) {
            completed = command;
            completions.incrementAndGet();
        }

        @Override
        public void fail(FailCommand command) {
            reviewRequired = command.uncertainSideEffect();
            failures.incrementAndGet();
        }
    }
}
