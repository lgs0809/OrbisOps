package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemoryCapturePolicy;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryCaptureApplicationServiceTest {
    private final IConversationMemoryRepository repository = mock(IConversationMemoryRepository.class);
    private final HotMemoryWritePort hot = mock(HotMemoryWritePort.class);
    private final MemoryPostProcessingApplicationService post = mock(MemoryPostProcessingApplicationService.class);
    private final MemoryCaptureFailurePort failures = mock(MemoryCaptureFailurePort.class);

    @Test
    void persistsSequenceAndReplayFactsBeforeCacheAndDispatch() {
        persisted();
        var result = service().capture(command());
        assertTrue(result.captured());
        assertEquals(42L, result.message().metadata().get("messageSeq"));
        var order = inOrder(repository, hot, post);
        order.verify(repository).capture(any(), eq(24));
        order.verify(hot).append(result.message(), 24);
        order.verify(post).submit(any());
    }

    @Test
    void databaseFailureDoesNotCreateAnUndurableCacheOrDispatchedTask() {
        when(repository.capture(any(), anyInt())).thenThrow(new IllegalStateException("database unavailable"));
        assertFalse(service().capture(command()).captured());
        verifyNoInteractions(hot, post);
        verify(failures).onFailure(eq("capture"), any());
    }

    @Test
    void cacheFailureStillDispatchesCommittedWork() {
        persisted();
        doThrow(new IllegalStateException("cache unavailable")).when(hot).append(any(), anyInt());
        assertTrue(service().capture(command()).captured());
        verify(post).submit(any());
        verify(failures).onFailure(eq("hot-projection"), any());
    }

    @Test
    void dispatchFailureDoesNotUndoCommittedCapture() {
        persisted();
        doThrow(new IllegalStateException("executor rejected")).when(post).submit(any());
        assertTrue(service().capture(command()).captured());
        verify(failures).onFailure(eq("post-processing-submit"), any());
    }

    @Test
    void invalidInputHasNoEffects() {
        assertFalse(service().capture(null).captured());
        verifyNoInteractions(repository, hot, post);
    }

    private void persisted() {
        when(repository.capture(any(), anyInt())).thenReturn(new ColdMemoryMessageSnapshot("s1", "u1", "user",
                "只读排查", "2026-09-08 00:00:00", Map.of("projectId", "A", "messageSeq", 42L, "turn_index", 42L)));
    }

    private MemoryCaptureCommand command() {
        return new MemoryCaptureCommand("s1", "u1", "user", "只读排查", Map.of("projectId", "A", "turn_index", 999), 24, true);
    }

    private MemoryCaptureApplicationService service() {
        return new MemoryCaptureApplicationService(hot, null, post, new MemoryCapturePolicy(),
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC), failures, repository);
    }
}
