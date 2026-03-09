package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryRuntimeQuery;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernedMemoryTypedUseCasesTest {

    @Test
    void explicitCaptureBuildsTypedMemoryCommandWithoutLearningSideEffects() {
        GovernedMemoryApplicationService memoryApplication = mock(GovernedMemoryApplicationService.class);
        GovernedMemorySnapshot stored = snapshot(MemoryType.USER_WORKFLOW, false);
        GovernedMemoryCreationResult creation = GovernedMemoryCreationResult.created(stored, "");
        when(memoryApplication.create(any())).thenReturn(creation);
        CaptureMemoryUseCase useCase = new CaptureMemoryUseCase(memoryApplication);

        CaptureMemoryResult result = useCase.capture(new CaptureMemoryCommand(
                "请记住排查问题先看日志再看指标",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                "run-1",
                "MEDIUM"));

        ArgumentCaptor<GovernedMemoryCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(GovernedMemoryCreateCommand.class);
        verify(memoryApplication).create(commandCaptor.capture());
        GovernedMemoryCreateCommand command = commandCaptor.getValue();
        assertEquals("USER_WORKFLOW", command.memoryType());
        assertEquals("USER", command.scopeType());
        assertEquals("user-1", command.scopeId());
        assertEquals("USER_ASSERTED", command.sourceType());
        assertEquals("MEDIUM", command.riskLevel());
        assertEquals(creation, result.memory());
    }

    @Test
    void explicitPreferenceCaptureReturnsOnlyCommittedMemory() {
        GovernedMemoryApplicationService memoryApplication = mock(GovernedMemoryApplicationService.class);
        GovernedMemoryCreationResult creation = GovernedMemoryCreationResult.created(
                snapshot(MemoryType.USER_PREFERENCE, false), "");
        when(memoryApplication.create(any())).thenReturn(creation);
        CaptureMemoryUseCase useCase = new CaptureMemoryUseCase(memoryApplication);

        CaptureMemoryResult result = useCase.capture(new CaptureMemoryCommand(
                "请记住回答优先使用中文",
                "user-1", "demo-project", "agent-1", "session-1", "run-1", ""));

        assertEquals(creation, result.memory());
    }

    @Test
    void runtimeQueryUsesTypedCriteriaAndSnapshots() {
        GovernedMemoryApplicationService memoryApplication = mock(GovernedMemoryApplicationService.class);
        GovernedMemorySnapshot snapshot = snapshot(MemoryType.PROJECT_FACT, false);
        when(memoryApplication.selectForRuntime(any())).thenReturn(List.of(snapshot));
        QueryRuntimeMemoryUseCase useCase = new QueryRuntimeMemoryUseCase(memoryApplication);

        List<GovernedMemorySnapshot> result = useCase.select(
                " user-1 ", " demo-project ", " session-1 ", " run-1 ", 100);

        ArgumentCaptor<GovernedMemoryRuntimeQuery> queryCaptor =
                ArgumentCaptor.forClass(GovernedMemoryRuntimeQuery.class);
        verify(memoryApplication).selectForRuntime(queryCaptor.capture());
        assertEquals(50, queryCaptor.getValue().limit());
        assertEquals("user-1", queryCaptor.getValue().userId());
        assertEquals(List.of(snapshot), result);
    }

    @Test
    void verifyUsesTypedSnapshotAndPreservesValidationCodes() {
        GovernedMemoryApplicationService memoryApplication = mock(GovernedMemoryApplicationService.class);
        GovernedMemorySnapshot before = snapshot(MemoryType.PROJECT_FACT, false);
        GovernedMemorySnapshot after = snapshot(MemoryType.PROJECT_FACT, true);
        when(memoryApplication.require("memory-1")).thenReturn(before);
        when(memoryApplication.verifyProjectFact(any())).thenReturn(after);
        VerifyProjectFactUseCase useCase = new VerifyProjectFactUseCase(memoryApplication);

        GovernedMemorySnapshot result = useCase.verify(
                " memory-1 ", List.of(Map.of("proofId", "proof-1")), " user-1 ");

        assertEquals(after, result);
        ArgumentCaptor<GovernedMemoryVerifyCommand> commandCaptor =
                ArgumentCaptor.forClass(GovernedMemoryVerifyCommand.class);
        verify(memoryApplication).verifyProjectFact(commandCaptor.capture());
        assertEquals("memory-1", commandCaptor.getValue().memoryId());
        assertEquals("user-1", commandCaptor.getValue().actor());

        when(memoryApplication.require("memory-other")).thenReturn(
                snapshot(MemoryType.PROJECT_CONTEXT, false));
        assertEquals("MEMORY_PROJECT_FACT_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> useCase.verify("memory-other", List.of(Map.of("proofId", "p")), "user-1"))
                        .getMessage());
        assertEquals("MEMORY_PROJECT_FACT_PROOF_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> useCase.verify("memory-1", List.of(), "user-1"))
                        .getMessage());
    }

    private GovernedMemorySnapshot snapshot(MemoryType type, boolean verified) {
        return new GovernedMemorySnapshot(
                1L,
                "memory-1",
                type == MemoryType.USER_WORKFLOW || type == MemoryType.USER_PREFERENCE
                        ? MemoryScope.USER
                        : MemoryScope.PROJECT,
                type == MemoryType.USER_WORKFLOW || type == MemoryType.USER_PREFERENCE
                        ? "user-1"
                        : "demo-project",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                type,
                "logical",
                "content",
                "normalized",
                "USER_ASSERTED",
                "run-1",
                verified,
                verified ? 0.9D : 0.75D,
                "LOW",
                "ACTIVE",
                1,
                "hash-1",
                List.of(),
                null,
                "user-1",
                "idempotency-1",
                Instant.parse("2026-07-22T01:00:00Z"),
                Instant.parse("2026-07-22T01:00:00Z"));
    }
}
