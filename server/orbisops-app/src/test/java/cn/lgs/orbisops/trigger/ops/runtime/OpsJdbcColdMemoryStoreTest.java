package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.ColdMemoryStoreApplicationService;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsJdbcColdMemoryStoreTest {

    @Test
    void mapsTriggerModelsAndDelegatesToApplicationService() {
        ColdMemoryStoreApplicationService applicationService = mock(ColdMemoryStoreApplicationService.class);
        OpsJdbcColdMemoryStore store = new OpsJdbcColdMemoryStore(applicationService);

        store.appendMessage(OpsMemoryMessage.builder()
                .sessionId("session-1")
                .userId("user-1")
                .role("user")
                .content("hello")
                .metadata(Map.of("turn_index", 1))
                .build());
        store.saveItems(List.of(OpsMemoryItem.builder()
                .sessionId("session-1")
                .userId("user-1")
                .memoryType("fact")
                .content("memory")
                .importance(BigDecimal.valueOf(0.8))
                .build()));

        ArgumentCaptor<ColdMemoryMessageSnapshot> messageCaptor =
                ArgumentCaptor.forClass(ColdMemoryMessageSnapshot.class);
        verify(applicationService).appendMessage(messageCaptor.capture());
        assertEquals("hello", messageCaptor.getValue().content());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ColdMemoryItemSnapshot>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(applicationService).saveItems(itemsCaptor.capture());
        assertEquals("fact", itemsCaptor.getValue().get(0).memoryType());
    }

    @Test
    void mapsApplicationReadModelBackToLegacyView() {
        ColdMemoryStoreApplicationService applicationService = mock(ColdMemoryStoreApplicationService.class);
        when(applicationService.listItems("session-1", "user-1", 10)).thenReturn(List.of(
                new ColdMemoryItemSnapshot(
                        "session-1",
                        "user-1",
                        "summary",
                        "compressed",
                        BigDecimal.valueOf(0.86),
                        "[]",
                        "system",
                        "hash",
                        Map.of(),
                        "2026-07-21 18:00:00")));
        OpsJdbcColdMemoryStore store = new OpsJdbcColdMemoryStore(applicationService);

        List<OpsMemoryItem> items = store.listItems("session-1", "user-1", 10);
        store.clear("session-1");

        assertEquals(1, items.size());
        assertEquals("compressed", items.get(0).getContent());
        verify(applicationService).clear("session-1");
    }
}
