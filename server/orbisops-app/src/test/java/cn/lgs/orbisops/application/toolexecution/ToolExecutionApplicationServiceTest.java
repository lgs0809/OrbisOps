package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionApplicationServiceTest {

    @Test
    void shouldExecuteAllowedLifecycleInOrder() {
        List<String> events = new ArrayList<>();
        ToolExecutionApplicationService service = service(
                ToolExecutionDecision.allowed("MEDIUM", Map.of()),
                (target, request) -> {
                    events.add("dispatch");
                    return Map.of("status", "SUCCEEDED", "value", 7);
                },
                command -> {
                    events.add("record");
                    return recorded(command.durationMs());
                },
                (request, type, payload) -> events.add(type),
                event -> events.add("audit-" + event.action()));

        ToolExecutionResponse response = service.execute(request());

        assertTrue(response.allowed());
        assertEquals(7, response.payload().get("value"));
        assertEquals(List.of(
                "TOOL_EXECUTION_STARTED", "dispatch", "record",
                "TOOL_EXECUTION_COMPLETED", "audit-allowed"), events);
    }

    @Test
    void blockedDecisionMustStillRecordUnverifiedEvidenceWithoutDispatch() {
        AtomicBoolean dispatched = new AtomicBoolean(false);
        List<ToolExecutionRecordPort.ToolExecutionRecordCommand> records = new ArrayList<>();
        ToolExecutionApplicationService service = service(
                new ToolExecutionDecision(false, "BLOCKED", "POLICY_BLOCKED", "denied", "HIGH", Map.of()),
                (target, request) -> {
                    dispatched.set(true);
                    return Map.of();
                },
                command -> {
                    records.add(command);
                    return recorded(command.durationMs());
                },
                (request, type, payload) -> { },
                event -> { });

        ToolExecutionResponse response = service.execute(request());

        assertFalse(response.allowed());
        assertFalse(dispatched.get());
        assertEquals(1, records.size());
        assertEquals("TOOL_BLOCKED", records.get(0).source());
        assertFalse(records.get(0).verifiedEvidence());
    }

    @Test
    void failureMustCheckpointAndAuditThenRethrow() {
        List<String> events = new ArrayList<>();
        ToolExecutionApplicationService service = service(
                ToolExecutionDecision.allowed("MEDIUM", Map.of()),
                (target, request) -> {
                    throw new IllegalStateException("boom");
                },
                command -> recorded(command.durationMs()),
                (request, type, payload) -> events.add(type),
                event -> events.add("audit-" + event.action()));

        assertThrows(IllegalStateException.class, () -> service.execute(request()));
        assertEquals(List.of("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_FAILED", "audit-failed"), events);
    }

    @Test
    void readOnlyVerificationRestrictionBlocksOtherwiseAllowedWriteBeforeDispatch() {
        AtomicBoolean dispatched = new AtomicBoolean();
        List<ToolExecutionRecordPort.ToolExecutionRecordCommand> records = new ArrayList<>();
        var service = service(ToolExecutionDecision.allowed("MEDIUM", Map.of()),
                (target, request) -> { dispatched.set(true); return Map.of(); },
                command -> { records.add(command); return recorded(0); },
                (request, type, payload) -> {}, event -> {});
        var request = request();
        var restricted = new ToolExecutionRequest(request.projectId(), request.userId(), request.actor(),
                request.toolsetId(), request.toolName(), request.scope(), request.arguments(),
                request.sessionId(), request.runId(), Map.of("requireReadOnly", true), request.landingContext());
        var response = service.execute(restricted);
        assertFalse(response.allowed());
        assertFalse(dispatched.get());
        assertEquals("READ_ONLY_REQUIRED", response.payload().get("reasonCode"));
        assertEquals(1, records.size());
        assertFalse(records.get(0).verifiedEvidence());
    }

    private ToolExecutionApplicationService service(
            ToolExecutionDecision decision,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit) {
        ToolExecutionTarget target = target();
        AtomicLong nanos = new AtomicLong();
        return new ToolExecutionApplicationService(
                request -> new ToolExecutionResolution(target, decision),
                dispatch,
                records,
                checkpoints,
                audit,
                () -> "tool-call-1",
                () -> nanos.addAndGet(1_000_000L));
    }

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of("command", "pwd"),
                "session-1", "run-1", Map.of(), Map.of());
    }

    private ToolExecutionTarget target() {
        return new ToolExecutionTarget(
                "code.repair", "code_bash", "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
    }

    private ToolExecutionRecordedResult recorded(long durationMs) {
        return new ToolExecutionRecordedResult(
                "tool-result-1", "evidence-1", "preview", "a".repeat(64), false,
                "db:tool-result-1", "b".repeat(64), durationMs);
    }
}
