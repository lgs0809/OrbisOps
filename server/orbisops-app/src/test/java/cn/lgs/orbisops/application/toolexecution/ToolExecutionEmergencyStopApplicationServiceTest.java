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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionEmergencyStopApplicationServiceTest {

    @Test
    void stopBlocksNewSideEffectsWithoutConsumingRetryAndReleaseDoesNotBypassApproval() {
        AtomicBoolean stopped = new AtomicBoolean(true);
        AtomicInteger sideEffects = new AtomicInteger();
        List<String> facts = new ArrayList<>();
        ToolExecutionApplicationService service = service(
                writeTarget(),
                (request, target) -> stopped.get(),
                sideEffects,
                facts);
        ToolExecutionRequest approved = approvedRequest();

        ToolExecutionResponse blocked = service.execute(approved);
        assertFalse(blocked.allowed());
        assertEquals("TOOL_EXECUTION_EMERGENCY_STOP_ACTIVE", blocked.payload().get("reasonCode"));
        assertEquals(0, sideEffects.get());
        assertTrue(facts.contains("TOOL_EXECUTION_BLOCKED"));
        assertTrue(facts.contains("audit-emergency_stop_blocked"));

        stopped.set(false);
        ToolExecutionResponse resumed = service.execute(approved);

        assertTrue(resumed.allowed());
        assertEquals(1, sideEffects.get());
        assertEquals(ToolExecutionScope.APPROVED_LANDING, resumed.scope());
    }

    @Test
    void stopNeverBlocksReadOnlyEvidenceCollection() {
        AtomicInteger dispatches = new AtomicInteger();
        ToolExecutionApplicationService service = service(
                readTarget(),
                (request, target) -> true,
                dispatches,
                new ArrayList<>());

        ToolExecutionResponse response = service.execute(readRequest());

        assertTrue(response.allowed());
        assertEquals(1, dispatches.get());
    }

    private ToolExecutionApplicationService service(
            ToolExecutionTarget target,
            ToolExecutionEmergencyStopPort stop,
            AtomicInteger dispatches,
            List<String> facts) {
        AtomicLong nanos = new AtomicLong();
        return new ToolExecutionApplicationService(
                request -> new ToolExecutionResolution(
                        target, ToolExecutionDecision.allowed("HIGH", Map.of())),
                (resolved, request) -> {
                    dispatches.incrementAndGet();
                    return Map.of("status", "SUCCEEDED");
                },
                command -> recorded(command.durationMs()),
                (request, type, payload) -> facts.add(type),
                event -> facts.add("audit-" + event.action()),
                ToolExecutionIdempotencyPort.disabled(),
                ToolExecutionTransactionPort.direct(),
                stop,
                () -> "tool-call-1",
                () -> nanos.addAndGet(1_000_000L),
                Clock.systemUTC());
    }

    private ToolExecutionRequest approvedRequest() {
        return new ToolExecutionRequest(
                "project-1", "operator", "operator", "prod.change", "restart",
                ToolExecutionScope.APPROVED_LANDING,
                Map.of("service", "demo-project"),
                "session-1", "landing-run-1",
                Map.of("idempotencyKey", "landing:package-1:operation-1"),
                Map.of(
                        "changePackageId", "package-1",
                        "approvedPackageVersion", "1",
                        "approvedPackageHash", "a".repeat(64)));
    }

    private ToolExecutionRequest readRequest() {
        return new ToolExecutionRequest(
                "project-1", "operator", "operator", "metrics", "query",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of(), "session-1", "run-1", Map.of(), Map.of());
    }

    private ToolExecutionTarget writeTarget() {
        return new ToolExecutionTarget(
                "prod.change", "restart", "MCP", "HIGH",
                false, false, true, true, true);
    }

    private ToolExecutionTarget readTarget() {
        return new ToolExecutionTarget(
                "metrics", "query", "MCP", "LOW",
                true, false, false, false, false);
    }

    private ToolExecutionRecordedResult recorded(long durationMs) {
        return new ToolExecutionRecordedResult(
                "tool-result-1", "evidence-1", "preview", "a".repeat(64), false,
                "db:tool-result-1", "b".repeat(64), durationMs);
    }
}
