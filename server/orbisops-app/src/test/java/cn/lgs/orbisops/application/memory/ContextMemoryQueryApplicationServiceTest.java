package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextMemoryQueryApplicationServiceTest {

    @Test
    void normalizesOptionalFiltersAndBuildsTypedCriteria() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        when(store.search(any())).thenReturn(List.of(snapshot("ctx-1")));
        ContextMemoryQueryApplicationService service = service(store);

        List<ContextMemorySnapshot> result = service.search(new ContextMemoryQuery(
                " project ",
                " demo-project ",
                " project_context ",
                " active ",
                999));

        assertEquals(List.of("ctx-1"), result.stream().map(ContextMemorySnapshot::memoryId).toList());
        ArgumentCaptor<ContextMemorySearchCriteria> captor =
                ArgumentCaptor.forClass(ContextMemorySearchCriteria.class);
        verify(store).search(captor.capture());
        ContextMemorySearchCriteria criteria = captor.getValue();
        assertEquals("PROJECT", criteria.scopeType());
        assertEquals("demo-project", criteria.scopeId());
        assertEquals("PROJECT_CONTEXT", criteria.memoryType());
        assertEquals("ACTIVE", criteria.status());
        assertEquals(500, criteria.limit());
    }

    @Test
    void invalidOptionalFiltersBecomeUnfilteredCriteria() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        when(store.search(any())).thenReturn(List.of());
        ContextMemoryQueryApplicationService service = service(store);

        service.search(new ContextMemoryQuery("invalid", "id", "unknown", "deleted", 0));

        ArgumentCaptor<ContextMemorySearchCriteria> captor =
                ArgumentCaptor.forClass(ContextMemorySearchCriteria.class);
        verify(store).search(captor.capture());
        assertEquals("", captor.getValue().scopeType());
        assertEquals("", captor.getValue().memoryType());
        assertEquals("", captor.getValue().status());
        assertEquals(1, captor.getValue().limit());
    }

    @Test
    void nullQueryOrMissingStoreKeepsSearchFailOpen() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        ContextMemoryQueryApplicationService service = service(store);

        assertEquals(List.of(), service.search(null));
        assertEquals(List.of(), new ContextMemoryQueryApplicationService(
                null,
                new ContextMemoryDefinitionPolicy()).search(
                new ContextMemoryQuery("", "", "", "", 10)));
        verifyNoInteractions(store);
    }

    @Test
    void requireDelegatesIdentityLookupAndMissingStoreFailsClosed() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        when(store.require("ctx-1")).thenReturn(snapshot("ctx-1"));
        ContextMemoryQueryApplicationService service = service(store);

        assertEquals("ctx-1", service.require("ctx-1").memoryId());
        verify(store).require("ctx-1");
        assertThrows(IllegalStateException.class, () -> new ContextMemoryQueryApplicationService(
                null,
                new ContextMemoryDefinitionPolicy()).require("ctx-1"));
    }

    private ContextMemoryQueryApplicationService service(ContextMemoryStoreApplicationService store) {
        return new ContextMemoryQueryApplicationService(
                store,
                new ContextMemoryDefinitionPolicy());
    }

    private ContextMemorySnapshot snapshot(String memoryId) {
        return new ContextMemorySnapshot(
                null,
                memoryId,
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "title",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                BigDecimal.valueOf(0.8D),
                "manual",
                "session-1",
                "source-hash",
                "user-1",
                "",
                "",
                "");
    }
}
