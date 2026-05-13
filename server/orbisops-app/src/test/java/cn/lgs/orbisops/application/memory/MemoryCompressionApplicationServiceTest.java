package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.ConversationMemoryWindow;
import cn.lgs.orbisops.domain.memory.service.MemoryCompressionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryCompressionApplicationServiceTest {
    private final IConversationMemoryRepository repository = mock(IConversationMemoryRepository.class);

    @Test
    void authoritativeWindowAndCoverageAreUsedInsteadOfCallerCache() {
        var window = window(4);
        when(repository.window("s1", 256, false)).thenReturn(Optional.of(window));
        when(repository.commitSummary(any(), anyLong(), anyString(), anyList(), anyString())).thenReturn(true);
        var model = mock(MemoryModelSummaryPort.class);
        when(model.summarize(anyList(), eq(4000))).thenReturn("模型摘要");
        assertTrue(service(model).compress(command(true)));
        verify(repository).commitSummary(eq(window), eq(2L), eq("模型摘要"), anyList(), eq("llm_context_compressor"));
    }

    @Test
    void modelFailureFallsBackToRulesAndPreservesFullConstraintReference() {
        when(repository.window(anyString(), anyInt(), anyBoolean())).thenReturn(Optional.of(window(4)));
        when(repository.commitSummary(any(), anyLong(), anyString(), anyList(), anyString())).thenReturn(true);
        var model = mock(MemoryModelSummaryPort.class);
        when(model.summarize(anyList(), anyInt())).thenThrow(new IllegalStateException("model down"));
        assertTrue(service(model).compress(command(true)));
        verify(repository).commitSummary(any(), eq(2L), contains("禁止重启"),
                argThat(items -> items.stream().anyMatch(m -> m.content().contains("禁止重启"))), eq("context_compressor"));
    }

    @Test
    void lateSummaryIsRejectedAndCanBeRetriedFromNewSnapshot() {
        when(repository.window(anyString(), anyInt(), anyBoolean())).thenReturn(Optional.of(window(4)));
        when(repository.commitSummary(any(), anyLong(), anyString(), anyList(), anyString())).thenReturn(false);
        assertEquals("MEMORY_SUMMARY_REVISION_CHANGED", assertThrows(IllegalStateException.class,
                () -> service(null).compress(command(false))).getMessage());
    }

    @Test
    void disabledModelIsNeverCalled() {
        when(repository.window(anyString(), anyInt(), anyBoolean())).thenReturn(Optional.of(window(4)));
        when(repository.commitSummary(any(), anyLong(), anyString(), anyList(), anyString())).thenReturn(true);
        var model = mock(MemoryModelSummaryPort.class);
        assertTrue(service(model).compress(command(false)));
        verifyNoInteractions(model);
    }

    @Test
    void belowThresholdOrMissingSourceDoesNotCommit() {
        when(repository.window(anyString(), anyInt(), anyBoolean())).thenReturn(Optional.of(window(3)));
        assertFalse(service(null).compress(command(false)));
        assertFalse(service(null).compress(null));
        verify(repository, never()).commitSummary(any(), anyLong(), anyString(), anyList(), anyString());
    }

    @Test
    void noDurableStoreNeverFallsBackToDestructiveWindowReplacement() {
        var hot = mock(HotMemoryReplacePort.class);
        var cold = mock(ColdMemoryStoreApplicationService.class);
        var service = new MemoryCompressionApplicationService(null, null, hot, cold, () -> "now");
        assertFalse(service.compress(command(false)));
        verifyNoInteractions(hot, cold);
        assertTrue(service.trimContext("head-" + "x".repeat(3000) + "-tail", 1000).endsWith("-tail"));
    }

    private MemoryCompressionApplicationService service(MemoryModelSummaryPort model) {
        return new MemoryCompressionApplicationService(new MemoryCompressionPolicy(new MemoryContentHashPolicy()), model, repository, () -> "now");
    }

    private MemoryCompressionCommand command(boolean model) {
        return new MemoryCompressionCommand("s1", "u1", List.of(), 4, 2, model, 4000, 24);
    }

    private ConversationMemoryWindow window(int count) {
        var messages = java.util.stream.IntStream.rangeClosed(1, count).mapToObj(i -> new ColdMemoryMessageSnapshot(
                "s1", "u1", "user", i == 1 ? "禁止重启，只读检查" : "内容" + i, "now", Map.of("messageSeq", (long) i))).toList();
        return new ConversationMemoryWindow("s1", "A", "u1", count, 0, 0, "", List.of(), messages);
    }
}
