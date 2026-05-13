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

class MemoryRetrievalApplicationServiceTest {

    @Test
    void retrievesFourTypedMemorySlices() {
        ColdMemoryItemSnapshot coldItem = item("cold");
        ColdMemoryStoreApplicationService coldStore = new ColdMemoryStoreApplicationService(
                repository(List.of(coldItem)),
                () -> true,
                null);
        MemoryMessageView hotMessage = message("hot");
        MemoryMessageView semanticMessage = message("semantic");
        ContextMemoryView contextMemory = context("context-1");
        MemoryRetrievalApplicationService service = new MemoryRetrievalApplicationService(
                coldStore,
                (sessionId, limit) -> List.of(hotMessage),
                (sessionId, userId, query, limit) -> List.of(semanticMessage),
                (scene, userId, projectId, limit) -> List.of(contextMemory),
                () -> null,
                null);

        MemoryRetrievalResult result = service.retrieve(query());

        assertEquals(List.of(coldItem), result.coldItems());
        assertEquals(List.of(hotMessage), result.hotMessages());
        assertEquals(List.of(semanticMessage), result.semanticMessages());
        assertEquals(List.of(contextMemory), result.contextMemories());
    }

    @Test
    void isolatesOneFailedSliceWithoutDroppingOtherResults() {
        List<String> failures = new ArrayList<>();
        ColdMemoryStoreApplicationService coldStore = new ColdMemoryStoreApplicationService(
                repository(List.of(item("cold"))),
                () -> true,
                null);
        MemoryRetrievalApplicationService service = new MemoryRetrievalApplicationService(
                coldStore,
                (sessionId, limit) -> List.of(message("hot")),
                (sessionId, userId, query, limit) -> {
                    throw new IllegalStateException("semantic down");
                },
                (scene, userId, projectId, limit) -> List.of(context("context-1")),
                () -> null,
                (slice, error) -> failures.add(slice + ":" + error.getMessage()));

        MemoryRetrievalResult result = service.retrieve(query());

        assertEquals(1, result.coldItems().size());
        assertEquals(1, result.hotMessages().size());
        assertEquals(List.of(), result.semanticMessages());
        assertEquals(1, result.contextMemories().size());
        assertEquals(List.of("semantic-messages:semantic down"), failures);
    }

    @Test
    void invalidSessionReturnsEmptyWithoutCallingPorts() {
        List<String> calls = new ArrayList<>();
        MemoryRetrievalApplicationService service = new MemoryRetrievalApplicationService(
                null,
                (sessionId, limit) -> {
                    calls.add("hot");
                    return List.of();
                },
                (sessionId, userId, query, limit) -> {
                    calls.add("semantic");
                    return List.of();
                },
                (scene, userId, projectId, limit) -> {
                    calls.add("context");
                    return List.of();
                },
                () -> null,
                null);

        MemoryRetrievalResult result = service.retrieve(new MemoryRetrievalQuery(
                " ", "u1", "query", "CHAT", "project", 1, 1, 1, 1, 100));

        assertEquals(MemoryRetrievalResult.empty(), result);
        assertEquals(List.of(), calls);
    }

    private MemoryRetrievalQuery query() {
        return new MemoryRetrievalQuery(
                "s1", "u1", "query", "CHAT", "project", 3, 4, 5, 6, 1000);
    }

    private MemoryMessageView message(String content) {
        return new MemoryMessageView("s1", "u1", "user", content, "2026-07-21 18:00:00", Map.of());
    }

    private ContextMemoryView context(String memoryId) {
        return new ContextMemoryView(
                memoryId,
                "PROJECT_CONTEXT",
                "PROJECT",
                "project",
                "title",
                "summary",
                "content",
                "hash",
                Map.of());
    }

    private ColdMemoryItemSnapshot item(String content) {
        return new ColdMemoryItemSnapshot(
                "s1",
                "u1",
                "fact",
                content,
                BigDecimal.valueOf(0.8),
                "[]",
                "user",
                "hash",
                Map.of(),
                "2026-07-21 18:00:00");
    }

    private IColdMemoryRepository repository(List<ColdMemoryItemSnapshot> items) {
        return new IColdMemoryRepository() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public void appendMessage(ColdMemoryMessageSnapshot message) {
            }

            @Override
            public void saveItems(List<ColdMemoryItemSnapshot> values) {
            }

            @Override
            public List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit) {
                return items;
            }

            @Override
            public void clear(String sessionId) {
            }
        };
    }
}
