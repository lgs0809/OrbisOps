package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsToolExecutionCheckpointAdapterTest {

    @Test
    void shouldSkipCheckpointOutsideDurableWorkSession() {
        OpsWorkSessionRunAdapter workSessions = mock(OpsWorkSessionRunAdapter.class);
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(provider(workSessions));

        adapter.checkpoint(request(Map.of()), "TOOL_EXECUTION_STARTED", Map.of("toolCallId", "call-1"));

        verify(workSessions, never()).checkpointToolExecution(org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void shouldDelegateDurableCheckpointAndFailClosedWhenAdapterMissing() {
        OpsWorkSessionRunAdapter workSessions = mock(OpsWorkSessionRunAdapter.class);
        Map<String, Object> context = Map.of("metadata", Map.of("_workSessionAttemptId", "attempt-1"));
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(provider(workSessions));

        adapter.checkpoint(request(context), "TOOL_EXECUTION_COMPLETED", Map.of("resultId", "result-1"));

        verify(workSessions).checkpointToolExecution(eq(Map.of(
                        "metadata", Map.of("_workSessionAttemptId", "attempt-1"),
                        "projectId", "project-1",
                        "runId", "run-1",
                        "sessionId", "session-1",
                        "userId", "alice")),
                eq("TOOL_EXECUTION_COMPLETED"),
                eq(Map.of("resultId", "result-1")));
        assertThrows(IllegalStateException.class, () ->
                new OpsToolExecutionCheckpointAdapter(provider(null))
                        .checkpoint(request(context), "TOOL_EXECUTION_STARTED", Map.of()));
    }

    @Test
    void approvedLandingCompletionMustProjectWithoutWorkSessionAttempt() {
        OpsWorkSessionRunAdapter workSessions = mock(OpsWorkSessionRunAdapter.class);
        LandingOperationJournalApplicationService journal = mock(LandingOperationJournalApplicationService.class);
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(
                provider(workSessions), provider(journal));
        String outputHash = "a".repeat(64);
        Map<String, Object> payload = Map.of("resultId", "result-1", "outputHash", outputHash);

        adapter.checkpoint(landingRequest(), "TOOL_EXECUTION_COMPLETED", payload);

        verify(journal).payload(eq(Map.of(
                "resultId", "result-1",
                "outputHash", outputHash,
                "executionKey", "execution-1")));
        verify(journal).completeFromToolExecution(
                eq("landing-run-1"), eq("restart-service"), eq("restart_service"),
                eq("execution-1"), any());
        verify(workSessions, never()).checkpointToolExecution(
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void approvedLandingCompletionWithoutOperationIdResolvesUniqueFrozenOperation() {
        OpsWorkSessionRunAdapter workSessions = mock(OpsWorkSessionRunAdapter.class);
        LandingOperationJournalApplicationService journal = mock(LandingOperationJournalApplicationService.class);
        when(journal.operationIdsForTool(
                "landing-run-1", "mcp.service-control", "restart_service"))
                .thenReturn(List.of("restart-service"));
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(
                provider(workSessions), provider(journal));
        String outputHash = "a".repeat(64);

        adapter.checkpoint(
                landingRequestWithoutOperationId(),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("resultId", "result-1", "outputHash", outputHash));

        verify(journal).completeFromToolExecution(
                eq("landing-run-1"), eq("restart-service"), eq("restart_service"),
                eq("execution-1"), any());
    }

    @Test
    void approvedLandingAuxiliaryToolWithoutFrozenOperationSkipsJournalProjection() {
        LandingOperationJournalApplicationService journal = mock(LandingOperationJournalApplicationService.class);
        when(journal.operationIdsForTool(
                "landing-run-1", "mcp.openapi", "openapi_list_operations"))
                .thenReturn(List.of());
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(
                provider(mock(OpsWorkSessionRunAdapter.class)), provider(journal));

        adapter.checkpoint(
                auxiliaryLandingRequest(),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("resultId", "result-1", "outputHash", "a".repeat(64)));

        verify(journal, never()).completeFromToolExecution(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                any());
    }

    @Test
    void approvedLandingAmbiguousFrozenOperationBindingFailsClosed() {
        LandingOperationJournalApplicationService journal = mock(LandingOperationJournalApplicationService.class);
        when(journal.operationIdsForTool(
                "landing-run-1", "mcp.service-control", "restart_service"))
                .thenReturn(List.of("restart-1", "restart-2"));
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(
                provider(mock(OpsWorkSessionRunAdapter.class)), provider(journal));

        assertThrows(IllegalStateException.class, () -> adapter.checkpoint(
                landingRequestWithoutOperationId(),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("resultId", "result-1", "outputHash", "a".repeat(64))));
    }

    @Test
    void preApprovalCompletionMustNotProjectLandingJournal() {
        LandingOperationJournalApplicationService journal = mock(LandingOperationJournalApplicationService.class);
        OpsToolExecutionCheckpointAdapter adapter = new OpsToolExecutionCheckpointAdapter(
                provider(mock(OpsWorkSessionRunAdapter.class)), provider(journal));

        adapter.checkpoint(
                request(Map.of("idempotencyKey", "execution-1")),
                "TOOL_EXECUTION_COMPLETED",
                Map.of("resultId", "result-1", "outputHash", "a".repeat(64)));

        verify(journal, never()).completeFromToolExecution(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                any());
    }

    private ToolExecutionRequest landingRequest() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "mcp.service-control", "restart_service",
                ToolExecutionScope.APPROVED_LANDING, Map.of("service", "order-service"),
                "session-1", "landing-run-1",
                Map.of("idempotencyKey", "execution-1"),
                Map.of(
                        "landingApproved", true,
                        "changePackageId", "cp-1",
                        "approvedPackageHash", "b".repeat(64),
                        "approvedPackageVersion", 1,
                        "operationId", "restart-service"));
    }

    private ToolExecutionRequest landingRequestWithoutOperationId() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "mcp.service-control", "restart_service",
                ToolExecutionScope.APPROVED_LANDING, Map.of("service", "order-service"),
                "session-1", "landing-run-1",
                Map.of("idempotencyKey", "execution-1"),
                Map.of(
                        "landingApproved", true,
                        "changePackageId", "cp-1",
                        "approvedPackageHash", "b".repeat(64),
                        "approvedPackageVersion", 1));
    }

    private ToolExecutionRequest auxiliaryLandingRequest() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "mcp.openapi", "openapi_list_operations",
                ToolExecutionScope.APPROVED_LANDING, Map.of(),
                "session-1", "landing-run-1",
                Map.of("idempotencyKey", "execution-read-1"),
                Map.of(
                        "landingApproved", true,
                        "changePackageId", "cp-1",
                        "approvedPackageHash", "b".repeat(64),
                        "approvedPackageVersion", 1));
    }

    private ToolExecutionRequest request(Map<String, Object> context) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of(),
                "session-1", "run-1", context, Map.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
