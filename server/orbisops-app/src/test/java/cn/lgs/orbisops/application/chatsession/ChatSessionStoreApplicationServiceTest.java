package cn.lgs.orbisops.application.chatsession;

import cn.lgs.orbisops.domain.chatsession.adapter.repository.IChatSessionRepository;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionStoreApplicationServiceTest {

    @Test
    void unavailableStoreFailsClosedWhenFallbackIsDisabled() {
        ChatSessionStoreApplicationService service = new ChatSessionStoreApplicationService(
                null,
                new ChatSessionMemoryCatalog(),
                () -> false,
                ignored -> { });

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.insert(session()));

        assertEquals("Chat Session Store 不可用，生产主链路禁止内存降级", error.getMessage());
    }

    @Test
    void unavailableStoreUsesExplicitFallbackWhenEnabled() {
        ChatSessionMemoryCatalog fallback = new ChatSessionMemoryCatalog();
        ChatSessionStoreApplicationService service = new ChatSessionStoreApplicationService(
                null,
                fallback,
                () -> true,
                ignored -> { });

        ChatSessionSnapshot saved = service.insert(session());

        assertEquals("session-1", saved.sessionId());
        assertEquals(1, service.fallbackSize());
        assertTrue(service.find("session-1").isPresent());
    }

    @Test
    void repositoryFailureIsObservedAndRoutedToFallbackOnlyWhenAllowed() {
        IChatSessionRepository repository = mock(IChatSessionRepository.class);
        when(repository.available()).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(repository).insert(any());
        AtomicBoolean allowFallback = new AtomicBoolean(true);
        AtomicInteger observed = new AtomicInteger();
        ChatSessionStoreApplicationService service = new ChatSessionStoreApplicationService(
                repository,
                new ChatSessionMemoryCatalog(),
                allowFallback::get,
                ignored -> observed.incrementAndGet());

        ChatSessionSnapshot saved = service.insert(session());

        assertEquals("session-1", saved.sessionId());
        assertEquals(1, observed.get());
        assertEquals(1, service.fallbackSize());

        allowFallback.set(false);
        assertThrows(IllegalStateException.class,
                () -> service.insert(new ChatSessionSnapshot(
                        "session-2", "owner", "project-1", "agent-1", "LATEST_PUBLISHED",
                        1, "a".repeat(64), "title", "AGENT", "GRAPH", false, "", "ACTIVE",
                        1L, Map.of(), "", "", 0, "")));
    }

    @Test
    void sessionVersionConflictIsNotDowngradedToFallback() {
        IChatSessionRepository repository = mock(IChatSessionRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.update(any())).thenReturn(false);
        ChatSessionStoreApplicationService service = new ChatSessionStoreApplicationService(
                repository,
                new ChatSessionMemoryCatalog(),
                () -> true,
                ignored -> { });

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.update(new ChatSessionUpdate(
                        "session-1", "new", "ACTIVE", Map.of(), 1L)));

        assertEquals("SESSION_VERSION_CONFLICT：会话已被其他用户或请求更新", error.getMessage());
        assertEquals(0, service.fallbackSize());
        verify(repository, never()).findById("session-1");
    }

    @Test
    void authoritativeInsertReturnsStoredSnapshotWhenAvailable() {
        IChatSessionRepository repository = mock(IChatSessionRepository.class);
        when(repository.available()).thenReturn(true);
        ChatSessionSnapshot stored = new ChatSessionSnapshot(
                "session-1", "owner", "project-1", "agent-1", "LATEST_PUBLISHED",
                1, "a".repeat(64), "stored", "AGENT", "GRAPH", false, "", "ACTIVE",
                1L, Map.of(), "", "", 0, "");
        when(repository.findById("session-1")).thenReturn(Optional.of(stored));
        ChatSessionStoreApplicationService service = new ChatSessionStoreApplicationService(
                repository,
                new ChatSessionMemoryCatalog(),
                () -> false,
                ignored -> { });

        ChatSessionSnapshot result = service.insert(session());

        assertEquals("stored", result.title());
        verify(repository).insert(any(ChatSessionSnapshot.class));
    }

    private ChatSessionSnapshot session() {
        return new ChatSessionSnapshot(
                "session-1",
                "owner",
                "project-1",
                "agent-1",
                "LATEST_PUBLISHED",
                1,
                "a".repeat(64),
                "title",
                "AGENT",
                "GRAPH",
                false,
                "",
                "ACTIVE",
                1L,
                Map.of(),
                "",
                "",
                0,
                "");
    }
}
