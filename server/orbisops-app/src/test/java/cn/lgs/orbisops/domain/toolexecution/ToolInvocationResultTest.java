package cn.lgs.orbisops.domain.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocationDisposition;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocationResult;
import cn.lgs.orbisops.domain.toolset.model.BoundToolReference;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolReference;
import cn.lgs.orbisops.domain.toolset.model.ToolSchema;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolInvocationResultTest {

    private static final String HASH = "a".repeat(64);
    private static final String RECEIPT_HASH = "b".repeat(64);

    @Test
    void approvedTargetWriteProjectsProductionExecutionAndReceipt() {
        ToolExecutionResult result = ToolExecutionResult.allowed(
                bound(ToolSemantics.targetResourceWrite()),
                Map.of(
                        "receiptId", "receipt-1",
                        "resultHash", RECEIPT_HASH,
                        "status", "SUCCEEDED"),
                recorded());

        ToolInvocationResult invocation = ToolInvocationResult.from(
                result, ToolExecutionScope.APPROVED_LANDING);

        assertEquals(ToolInvocationDisposition.SUCCEEDED, invocation.disposition());
        assertTrue(invocation.executedProductionAction());
        assertEquals("receipt-1", invocation.receiptId());
        assertEquals(RECEIPT_HASH, invocation.resultHash());
    }

    @Test
    void readonlyAndBlockedInvocationsNeverClaimProductionExecution() {
        ToolInvocationResult read = ToolInvocationResult.from(
                ToolExecutionResult.allowed(
                        bound(ToolSemantics.readOnlyTool()), Map.of(), recorded()),
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW);
        ToolInvocationResult blocked = ToolInvocationResult.from(
                ToolExecutionResult.blocked(
                        bound(ToolSemantics.targetResourceWrite()),
                        "APPROVAL_REQUIRED",
                        "approval missing",
                        Map.of("status", "BLOCKED"),
                        recorded()),
                ToolExecutionScope.APPROVED_LANDING);

        assertFalse(read.executedProductionAction());
        assertEquals(ToolInvocationDisposition.BLOCKED, blocked.disposition());
        assertFalse(blocked.executedProductionAction());
        assertEquals("APPROVAL_REQUIRED", blocked.reasonCode());
    }

    private BoundToolReference bound(ToolSemantics semantics) {
        return new BoundToolReference(
                "project-1",
                new ToolReference("ops", "update_threshold"),
                ToolProviderDescriptor.mcp("MCP"),
                semantics,
                ToolSchema.empty());
    }

    private ToolExecutionRecordedResult recorded() {
        return new ToolExecutionRecordedResult(
                "result-1", "evidence-1", "preview", HASH,
                false, "tool-result://result-1", HASH, 10L);
    }
}
