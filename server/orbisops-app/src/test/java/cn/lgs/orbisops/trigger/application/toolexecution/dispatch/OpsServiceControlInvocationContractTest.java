package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsServiceControlInvocationContractTest {

    private final OpsServiceControlInvocationContract contract = new OpsServiceControlInvocationContract();

    @Test
    void restartUsesPlatformIdentityIdempotencyAndDeadlineInsteadOfModelValues() {
        ToolExecutionRequest prepared = contract.prepare(restartTarget(), new ToolExecutionRequest(
                "demo-project", "alice", "landing-worker",
                "mcp.service-control", "restart_service",
                ToolExecutionScope.APPROVED_LANDING,
                Map.of(
                        "projectId", "forged",
                        "service", "order-service",
                        "expectedVersion", 3,
                        "executionKey", "forged-key",
                        "deadline", "2030-01-01T00:00:00Z",
                        "actor", "forged"),
                "session-1", "run-1",
                Map.of(
                        "idempotencyKey", "server-key",
                        "deadline", "2026-08-09T23:00:00Z"),
                Map.of("landingApproved", true)));

        assertEquals("demo-project", prepared.arguments().get("projectId"));
        assertEquals("landing-worker", prepared.arguments().get("actor"));
        assertEquals("server-key", prepared.arguments().get("executionKey"));
        assertEquals("2026-08-09T23:00:00Z", prepared.arguments().get("deadline"));
        assertEquals(3, prepared.arguments().get("expectedVersion"));

        Map<String, Object> provider = Map.ofEntries(
                Map.entry("status", "SUCCEEDED"),
                Map.entry("receiptId", "receipt-1"),
                Map.entry("operation", "restart_service"),
                Map.entry("executionKey", "server-key"),
                Map.entry("projectId", "demo-project"),
                Map.entry("service", "order-service"),
                Map.entry("actor", "landing-worker"),
                Map.entry("expectedVersion", 3),
                Map.entry("previousVersion", 3),
                Map.entry("currentVersion", 4),
                Map.entry("previousRestartCount", 2),
                Map.entry("currentRestartCount", 3),
                Map.entry("completedAt", "2026-08-09T22:20:00Z"),
                Map.entry("operationInputHash", "a".repeat(64)),
                Map.entry("hashVersion", 1),
                Map.entry("resultHash", "sha256:" + "b".repeat(64)));
        Map<?, ?> result = (Map<?, ?>) contract.validateOutput(
                restartTarget(), prepared, Map.of("providerResult", provider));
        assertEquals("receipt-1", result.get("receiptId"));
    }

    @Test
    void dryRunCannotClaimTargetWriteAndDoesNotAcceptProductionOnlyFields() {
        ToolExecutionRequest prepared = contract.prepare(dryRunTarget(), new ToolExecutionRequest(
                "demo-project", "alice", "alice",
                "mcp.service-control", "restart_service_dry_run",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of(
                        "service", "order-service",
                        "expectedVersion", 1,
                        "executionKey", "model-key",
                        "deadline", "2030-01-01T00:00:00Z"),
                "session-1", "run-1", Map.of(), Map.of()));

        assertFalse(prepared.arguments().containsKey("executionKey"));
        assertFalse(prepared.arguments().containsKey("deadline"));
        assertThrows(IllegalStateException.class, () -> contract.validateOutput(
                dryRunTarget(), prepared, Map.of("providerResult", Map.of(
                        "status", "PASSED",
                        "projectId", "demo-project",
                        "service", "order-service",
                        "writesTargetResource", true))));
    }

    private ToolExecutionTarget restartTarget() {
        return new ToolExecutionTarget(
                "mcp.service-control", "restart_service", "MCP", "HIGH",
                false, false, true, true, true);
    }

    private ToolExecutionTarget dryRunTarget() {
        return new ToolExecutionTarget(
                "mcp.service-control", "restart_service_dry_run", "MCP", "MEDIUM",
                true, false, false, false, false);
    }
}
