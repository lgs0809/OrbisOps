package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextMemorySceneQueryApplicationServiceTest {

    @Test
    void routesOpsSceneAcrossProjectAndUserTypesWithDedupAndFinalLimit() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        when(store.search(any())).thenAnswer(invocation -> {
            ContextMemorySearchCriteria criteria = invocation.getArgument(0);
            return switch (criteria.memoryType()) {
                case "PROJECT_GLOSSARY" -> List.of(snapshot("shared", "old glossary"));
                case "PROJECT_CONVENTION" -> List.of(
                        snapshot("shared", "new convention"),
                        snapshot("project-2", "convention"));
                case "USER_PREFERENCE" -> List.of(snapshot("user-1", "preference"));
                default -> List.of();
            };
        });
        ContextMemorySceneQueryApplicationService service = service(store);

        List<ContextMemorySnapshot> result = service.query(new ContextMemorySceneQuery(
                "OPS_TROUBLESHOOTING", "user-1", "demo-project", 3));

        assertEquals(List.of("shared", "project-2", "user-1"),
                result.stream().map(ContextMemorySnapshot::memoryId).toList());
        assertEquals("new convention", result.get(0).content());
        ArgumentCaptor<ContextMemorySearchCriteria> captor =
                ArgumentCaptor.forClass(ContextMemorySearchCriteria.class);
        verify(store, times(3)).search(captor.capture());
        List<ContextMemorySearchCriteria> criteria = captor.getAllValues();
        assertEquals("PROJECT", criteria.get(0).scopeType());
        assertEquals("PROJECT_GLOSSARY", criteria.get(0).memoryType());
        assertEquals("PROJECT_CONVENTION", criteria.get(1).memoryType());
        assertEquals("USER", criteria.get(2).scopeType());
        assertEquals("USER_PREFERENCE", criteria.get(2).memoryType());
        assertEquals(2, criteria.get(0).limit());
    }

    @Test
    void skipsUnavailableScopesAndUsesClampedFinalLimit() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        when(store.search(any())).thenAnswer(invocation -> {
            ContextMemorySearchCriteria criteria = invocation.getArgument(0);
            return List.of(snapshot(criteria.memoryType(), criteria.memoryType()));
        });
        ContextMemorySceneQueryApplicationService service = service(store);

        List<ContextMemorySnapshot> result = service.query(new ContextMemorySceneQuery(
                "DESIGN_DISCUSSION", "user-1", "", 50));

        assertEquals(List.of("USER_WORKFLOW", "USER_DOMAIN_FOCUS"),
                result.stream().map(ContextMemorySnapshot::memoryId).toList());
        ArgumentCaptor<ContextMemorySearchCriteria> captor =
                ArgumentCaptor.forClass(ContextMemorySearchCriteria.class);
        verify(store, times(2)).search(captor.capture());
        assertEquals(4, captor.getAllValues().get(0).limit());
    }

    @Test
    void nullQueryOrMissingApplicationStoreReturnsEmpty() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        ContextMemorySceneQueryApplicationService service = service(store);

        assertEquals(List.of(), service.query(null));
        assertEquals(List.of(), new ContextMemorySceneQueryApplicationService(
                null,
                new ContextMemoryDefinitionPolicy()).query(
                new ContextMemorySceneQuery("CHAT", "user", "project", 4)));
        verifyNoInteractions(store);
    }

    @Test
    void nullSearchResultsAndMemoriesWithoutIdentityAreIgnored() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        when(store.search(any())).thenReturn(null, List.of(snapshot("", "no identity")));
        ContextMemorySceneQueryApplicationService service = service(store);

        List<ContextMemorySnapshot> result = service.query(new ContextMemorySceneQuery(
                "CHAT", "user-1", "demo-project", 4));

        assertEquals(List.of(), result);
    }

    private ContextMemorySceneQueryApplicationService service(ContextMemoryStoreApplicationService store) {
        return new ContextMemorySceneQueryApplicationService(
                store,
                new ContextMemoryDefinitionPolicy());
    }

    private ContextMemorySnapshot snapshot(String memoryId, String content) {
        return new ContextMemorySnapshot(
                null,
                memoryId,
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                memoryId,
                content,
                content,
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
