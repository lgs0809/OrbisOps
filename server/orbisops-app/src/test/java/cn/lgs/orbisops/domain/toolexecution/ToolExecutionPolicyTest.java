package cn.lgs.orbisops.domain.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolexecution.service.ToolExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionPolicyTest {

    private final ToolExecutionPolicy policy = new ToolExecutionPolicy();

    @Test
    void shouldClassifySourceAndBuildBlockedPayload() {
        ToolExecutionRequest request = request(ToolExecutionScope.PRE_APPROVAL_WORKFLOW);
        ToolExecutionTarget target = target();
        ToolExecutionDecision decision = new ToolExecutionDecision(
                false, "DENIED", "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE",
                "requires approved package", "HIGH", Map.of("allowed", false));

        assertEquals("PRE_APPROVAL_WORKFLOW:code.repair:code_bash", policy.source(request));
        assertEquals("REPAIR_DIFF", policy.evidenceSourceType(policy.source(request)));
        assertEquals("BLOCKED", policy.outputStatus(Map.of("status", "SUCCEEDED"), "TOOL_BLOCKED"));
        assertFalse(policy.verifiedEvidence("TOOL_BLOCKED"));
        assertTrue(policy.verifiedEvidence("CONTROLLED_BASH_EXECUTED"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE",
                policy.blockedPayload(target, decision).get("reasonCode"));
    }

    @Test
    void shouldFlattenMapAndLimitRawPreview() {
        assertEquals("ok", policy.responsePayload(Map.of("status", "ok"), 3).get("status"));
        assertEquals("abc", policy.responsePayload("abcdef", 3).get("rawPreview"));
    }

    @Test
    void readRestrictionPreservesReadAccessAndExistingDenials() {
        var original = request(ToolExecutionScope.PRE_APPROVAL_WORKFLOW);
        var request = new ToolExecutionRequest(original.projectId(), original.userId(), original.actor(),
                original.toolsetId(), original.toolName(), original.scope(), original.arguments(),
                original.sessionId(), original.runId(), Map.of("requireReadOnly", true), Map.of());
        var read = new ToolExecutionTarget("inspect", "read", "MCP", "LOW", true, false, false, false, false);
        var allowed = ToolExecutionDecision.allowed("LOW", Map.of());
        assertEquals(allowed, policy.restrictToReadOnly(request, read, allowed));
        var denied = new ToolExecutionDecision(false, "BLOCKED", "PROJECT_ACCESS_DENIED", "denied", "HIGH", Map.of());
        assertEquals(denied, policy.restrictToReadOnly(request, read, denied));
        assertEquals(denied, policy.restrictToReadOnly(request, target(), denied));
    }

    private ToolExecutionRequest request(ToolExecutionScope scope) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash", scope,
                Map.of("command", "pwd"), "session-1", "run-1", Map.of(), Map.of());
    }

    private ToolExecutionTarget target() {
        return new ToolExecutionTarget(
                "code.repair", "code_bash", "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
    }
}
