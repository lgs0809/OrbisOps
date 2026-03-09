package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ColdMemoryStoreApplicationServiceTest {

    @Test
    void delegatesTypedOperationsToAvailableRepository() {
        IColdMemoryRepository repository = mock(IColdMemoryRepository.class);
        when(repository.available()).thenReturn(true);
        ColdMemoryItemSnapshot item = item("s1", "u1", "cold item");
        when(repository.listItems("s1", "u1", 2)).thenReturn(List.of(item));
        ColdMemoryStoreApplicationService service = new ColdMemoryStoreApplicationService(
                repository,
                () -> true,
                null);
        ColdMemoryMessageSnapshot message = new ColdMemoryMessageSnapshot(
                "s1", "u1", "user", "hello", "2026-07-21 18:00:00", Map.of());

        service.appendMessage(message);
        service.saveItems(List.of(item));
        List<ColdMemoryItemSnapshot> items = service.listItems(" s1 ", " u1 ", 2);
        service.clear(" s1 ");

        assertTrue(service.available());
        assertEquals(List.of(item), items);
        verify(repository).appendMessage(message);
        verify(repository).saveItems(List.of(item));
        verify(repository).listItems("s1", "u1", 2);
        verify(repository).clear("s1");
    }

    @Test
    void disabledStorePreservesNoopAndEmptyResultSemantics() {
        IColdMemoryRepository repository = mock(IColdMemoryRepository.class);
        ColdMemoryStoreApplicationService service = new ColdMemoryStoreApplicationService(
                repository,
                () -> false,
                null);

        service.appendMessage(new ColdMemoryMessageSnapshot("s1", "u1", "user", "hello", "", Map.of()));
        service.saveItems(List.of(item("s1", "u1", "fact")));
        List<ColdMemoryItemSnapshot> items = service.listItems("s1", "u1", 1);
        service.clear("s1");

        assertFalse(service.available());
        assertEquals(List.of(), items);
        verifyNoInteractions(repository);
    }

    @Test
    void repositoryFailuresAreObservedAndContained() {
        IColdMemoryRepository repository = mock(IColdMemoryRepository.class);
        when(repository.available()).thenReturn(true);
        RuntimeException appendFailure = new IllegalStateException("write failed");
        RuntimeException queryFailure = new IllegalStateException("query failed");
        doThrow(appendFailure).when(repository).appendMessage(org.mockito.ArgumentMatchers.any());
        when(repository.listItems("s1", "u1", 1)).thenThrow(queryFailure);
        List<String> failures = new ArrayList<>();
        ColdMemoryStoreApplicationService service = new ColdMemoryStoreApplicationService(
                repository,
                () -> true,
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));

        service.appendMessage(new ColdMemoryMessageSnapshot("s1", "u1", "user", "hello", "", Map.of()));
        List<ColdMemoryItemSnapshot> items = service.listItems("s1", "u1", 0);

        assertEquals(List.of(), items);
        assertEquals(List.of("append-message:write failed", "list-items:query failed"), failures);
    }

    private ColdMemoryItemSnapshot item(String sessionId, String userId, String content) {
        return new ColdMemoryItemSnapshot(
                sessionId,
                userId,
                "fact",
                content,
                BigDecimal.valueOf(0.8),
                "[]",
                "user",
                "hash",
                Map.of(),
                "2026-07-21 18:00:00");
    }
}
