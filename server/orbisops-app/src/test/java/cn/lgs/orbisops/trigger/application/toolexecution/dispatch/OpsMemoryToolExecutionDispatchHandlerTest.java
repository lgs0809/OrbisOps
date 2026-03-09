package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.memory.CaptureMemoryCommand;
import cn.lgs.orbisops.application.memory.CaptureMemoryResult;
import cn.lgs.orbisops.application.memory.CaptureMemoryUseCase;
import cn.lgs.orbisops.application.memory.GovernedMemoryCreationResult;
import cn.lgs.orbisops.application.memory.QueryRuntimeMemoryUseCase;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMemoryToolExecutionDispatchHandlerTest {

    @Test
    void explicitUpsertCommitsMemoryWithoutForegroundLearningPayload() {
        CaptureMemoryUseCase capture = mock(CaptureMemoryUseCase.class);
        GovernedMemoryCreationResult creation = GovernedMemoryCreationResult.created(snapshot(), "");
        when(capture.capture(any(CaptureMemoryCommand.class)))
                .thenReturn(new CaptureMemoryResult(creation));
        OpsMemoryToolExecutionDispatchHandler handler = handler(capture, null);

        Object result = handler.dispatch(target("memory_upsert"), request(
                "memory_upsert",
                Map.of("content", "请记住这个项目使用 JDK 17")));

        Map<?, ?> view = (Map<?, ?>) result;
        assertEquals("COMMITTED", view.get("status"));
        assertEquals("memory-1", view.get("memoryId"));
        assertEquals(true, view.get("visibleToCurrentRunByUserMessage"));
        assertEquals(false, view.get("automaticallyReinjectCurrentRun"));
        assertFalse(view.containsKey("skillEvolutionSignal"));

        ArgumentCaptor<CaptureMemoryCommand> captor = ArgumentCaptor.forClass(CaptureMemoryCommand.class);
        verify(capture).capture(captor.capture());
        assertEquals("请记住这个项目使用 JDK 17", captor.getValue().query());
        assertEquals("alice", captor.getValue().userId());
        assertEquals("project-1", captor.getValue().projectId());
        assertEquals("agent-1", captor.getValue().agentId());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals("run-1", captor.getValue().runId());
    }

    @Test
    void explicitUpsertFailsClosedWhenContentMissing() {
        OpsMemoryToolExecutionDispatchHandler handler = handler(mock(CaptureMemoryUseCase.class), null);
        assertEquals("MEMORY_CONTENT_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> handler.dispatch(target("memory_upsert"), request("memory_upsert", Map.of())))
                        .getMessage());
    }

    @Test
    void searchUsesGovernedRuntimeSelectionAndBoundsLimit() {
        QueryRuntimeMemoryUseCase query = mock(QueryRuntimeMemoryUseCase.class);
        when(query.select("alice", "project-1", "session-1", "", 100))
                .thenReturn(List.of(snapshot()));
        OpsMemoryToolExecutionDispatchHandler handler = handler(null, query);

        Object result = handler.dispatch(target("memory_search"), request(
                "memory_search",
                Map.of("limit", 999)));

        Map<?, ?> view = (Map<?, ?>) result;
        assertEquals(1, view.get("count"));
        verify(query).select("alice", "project-1", "session-1", "", 100);
    }

    private OpsMemoryToolExecutionDispatchHandler handler(
            CaptureMemoryUseCase capture,
            QueryRuntimeMemoryUseCase query) {
        return new OpsMemoryToolExecutionDispatchHandler(provider(capture), provider(query));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "memory", toolName, "MEMORY", "LOW",
                false, false, false, false, false);
    }

    private ToolExecutionRequest request(String toolName, Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1",
                "alice",
                "alice",
                "memory",
                toolName,
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                arguments,
                "session-1",
                "run-1",
                Map.of("agentId", "agent-1"),
                Map.of());
    }

    private GovernedMemorySnapshot snapshot() {
        return new GovernedMemorySnapshot(
                1L,
                "memory-1",
                MemoryScope.PROJECT,
                "project-1",
                "alice",
                "project-1",
                "agent-1",
                "session-1",
                MemoryType.PROJECT_FACT,
                "jdk-version",
                "项目使用 JDK 17",
                "项目使用 JDK 17",
                "USER_ASSERTED",
                "run-1",
                false,
                0.75D,
                "LOW",
                "ACTIVE",
                1,
                "hash-1",
                List.of(),
                null,
                "alice",
                "idem-1",
                Instant.parse("2026-08-07T01:00:00Z"),
                Instant.parse("2026-08-07T01:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
