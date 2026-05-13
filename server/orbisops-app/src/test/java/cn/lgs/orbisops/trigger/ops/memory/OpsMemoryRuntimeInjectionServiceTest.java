package cn.lgs.orbisops.trigger.ops.memory;

import cn.lgs.orbisops.application.memory.GovernedMemoryApplicationService;
import cn.lgs.orbisops.application.memory.QueryRuntimeMemoryUseCase;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsMemoryRuntimeInjectionServiceTest {

    @Test
    void projectsTypedMemoriesIntoPromptAndStableReferences() {
        GovernedMemoryApplicationService memoryApplication = mock(GovernedMemoryApplicationService.class);
        when(memoryApplication.selectForRuntime(any())).thenReturn(List.of(
                snapshot("memory-1", MemoryType.PROJECT_FACT, MemoryScope.PROJECT, false),
                snapshot("memory-2", MemoryType.USER_PREFERENCE, MemoryScope.USER, true)));
        QueryRuntimeMemoryUseCase query = new QueryRuntimeMemoryUseCase(memoryApplication);
        OpsMemoryRuntimeInjectionService service = new OpsMemoryRuntimeInjectionService(
                query,
                OpsMemoryRuntimeInjectionSettings.defaults());

        OpsMemorySelection selection = service.select(OpsAgentChatRequest.builder()
                .userId("user-1")
                .projectId("demo-project")
                .sessionId("session-1")
                .runId("run-1")
                .build());

        assertTrue(selection.context().contains("[PROJECT_FACT] content-memory-1"));
        assertTrue(selection.context().contains("用户陈述，尚未由工具验证"));
        assertTrue(selection.context().contains("[USER_PREFERENCE] content-memory-2"));
        assertEquals(2, selection.refs().size());
        assertEquals("memory-1", selection.refs().get(0).get("memoryId"));
        assertEquals("PROJECT", selection.refs().get(0).get("scope"));
        assertEquals(false, selection.refs().get(0).get("verified"));
        assertEquals("USER_PREFERENCE", selection.refs().get(1).get("memoryType"));
    }

    @Test
    void nullRequestAndEmptySelectionReturnEmptyProjection() {
        GovernedMemoryApplicationService memoryApplication = mock(GovernedMemoryApplicationService.class);
        when(memoryApplication.selectForRuntime(any())).thenReturn(List.of());
        QueryRuntimeMemoryUseCase query = new QueryRuntimeMemoryUseCase(memoryApplication);
        OpsMemoryRuntimeInjectionService service = new OpsMemoryRuntimeInjectionService(
                query,
                OpsMemoryRuntimeInjectionSettings.defaults());

        assertEquals("", service.select(null).context());
        assertEquals(List.of(), service.select(OpsAgentChatRequest.builder()
                .userId("user-1")
                .projectId("demo-project")
                .sessionId("session-1")
                .runId("run-1")
                .build()).refs());
    }

    private GovernedMemorySnapshot snapshot(
            String memoryId,
            MemoryType type,
            MemoryScope scope,
            boolean verified) {
        return new GovernedMemorySnapshot(
                1L,
                memoryId,
                scope,
                scope == MemoryScope.USER ? "user-1" : "demo-project",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                type,
                "logical-" + memoryId,
                "content-" + memoryId,
                "normalized-" + memoryId,
                "USER_ASSERTED",
                "run-source",
                verified,
                verified ? 0.9D : 0.7D,
                "LOW",
                "ACTIVE",
                2,
                "hash-" + memoryId,
                List.of(),
                null,
                "user-1",
                "idem-" + memoryId,
                Instant.parse("2026-07-22T01:00:00Z"),
                Instant.parse("2026-07-22T01:30:00Z"));
    }
}
