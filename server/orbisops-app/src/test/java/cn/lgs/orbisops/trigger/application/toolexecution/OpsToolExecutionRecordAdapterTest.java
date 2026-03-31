package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionRecordPort;
import cn.lgs.orbisops.domain.evidence.model.EvidenceDraft;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.evidence.model.ToolResultBudget;
import cn.lgs.orbisops.domain.evidence.model.ToolResultDraft;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsToolExecutionRecordAdapterTest {

    @Test
    void shouldPersistThroughTypedEvidenceApplicationsWithLegacyBudgetDefaults() {
        ToolResultApplicationService results = mock(ToolResultApplicationService.class);
        EvidenceApplicationService evidence = mock(EvidenceApplicationService.class);
        ToolResult stored = result();
        when(results.record(any(ToolResultDraft.class))).thenReturn(stored);
        when(evidence.record(any(EvidenceDraft.class))).thenReturn(proof());
        OpsToolExecutionRecordAdapter adapter = new OpsToolExecutionRecordAdapter(results, evidence);

        ToolExecutionRecordedResult recorded = adapter.record(new ToolExecutionRecordPort.ToolExecutionRecordCommand(
                request(), target(), "TOOL_BLOCKED", "TOOL", "BLOCKED",
                Map.of("status", "BLOCKED"), 9L, false));

        ArgumentCaptor<ToolResultDraft> resultDraft = ArgumentCaptor.forClass(ToolResultDraft.class);
        verify(results).record(resultDraft.capture());
        assertEquals(200, resultDraft.getValue().budget().maxRows());
        assertEquals(32 * 1024, resultDraft.getValue().budget().maxBytes());
        assertEquals(400, resultDraft.getValue().budget().maxLines());
        assertEquals(1000, resultDraft.getValue().budget().maxPoints());
        assertEquals(60, resultDraft.getValue().budget().maxTimeRangeMinutes());

        ArgumentCaptor<EvidenceDraft> evidenceDraft = ArgumentCaptor.forClass(EvidenceDraft.class);
        verify(evidence).record(evidenceDraft.capture());
        assertFalse(evidenceDraft.getValue().verified());
        assertEquals("tool-result-1", recorded.resultId());
        assertEquals("evidence-1", recorded.evidenceId());
    }

    @Test
    void persistedInputAndOutputMustBeStructurallyRedactedBeforeStorage() {
        ToolResultApplicationService results = mock(ToolResultApplicationService.class);
        EvidenceApplicationService evidence = mock(EvidenceApplicationService.class);
        when(results.record(any(ToolResultDraft.class))).thenReturn(result());
        when(evidence.record(any(EvidenceDraft.class))).thenReturn(proof());
        OpsToolExecutionRecordAdapter adapter = new OpsToolExecutionRecordAdapter(results, evidence);
        ToolExecutionRequest request = new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of(
                        "command", "deploy",
                        "headers", Map.of(
                                "Authorization", "Bearer top-secret-token",
                                "X-Api-Key", "api-key-value"),
                        "credentials", Map.of("password", "db-password")),
                "session-1", "run-1", Map.of(), Map.of());

        adapter.record(new ToolExecutionRecordPort.ToolExecutionRecordCommand(
                request,
                target(),
                "MCP_REMOTE_TOOL",
                "TOOL",
                "SUCCEEDED",
                Map.of(
                        "status", "SUCCEEDED",
                        "token", "provider-token",
                        "nested", Map.of("privateKey", "private-value")),
                9L,
                true));

        ArgumentCaptor<ToolResultDraft> draft = ArgumentCaptor.forClass(ToolResultDraft.class);
        verify(results).record(draft.capture());
        String query = draft.getValue().query();
        String output = draft.getValue().output();
        assertFalse(query.contains("top-secret-token"));
        assertFalse(query.contains("api-key-value"));
        assertFalse(query.contains("db-password"));
        assertFalse(output.contains("provider-token"));
        assertFalse(output.contains("private-value"));
        assertTrue(query.contains("***"));
        assertTrue(output.contains("***"));
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

    private ToolResult result() {
        return new ToolResult(
                "tool-result-1", "project-1", "session-1", "run-1", "alice",
                "code.repair", "code_bash", "TOOL_BLOCKED", "BLOCKED", "{}",
                "b".repeat(64), "preview", "full", "db:tool-result-1", "a".repeat(64),
                false, 9L, ToolResultBudget.defaults(), "alice", "2026-07-24T00:00:00Z");
    }

    private EvidenceRecord proof() {
        return new EvidenceRecord(
                "evidence-1", "project-1", "run-1", "TOOL", "code.repair/code_bash",
                "tool-result-1", "a".repeat(64), "db:tool-result-1", "preview", false,
                Map.of(), "c".repeat(64), "alice", "2026-07-24T00:00:00Z");
    }
}
