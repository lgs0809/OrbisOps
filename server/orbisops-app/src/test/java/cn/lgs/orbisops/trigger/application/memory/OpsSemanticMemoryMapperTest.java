package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.SemanticMemoryRetrievalQuery;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteCommand;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsSemanticMemoryMapperTest {

    private final OpsSemanticMemoryMapper mapper = new OpsSemanticMemoryMapper();

    @Test
    void mapsHistoricalMessageToTypedWriteCommand() {
        OpsMemoryMessage message = OpsMemoryMessage.builder()
                .sessionId("s1")
                .userId("u1")
                .role("user")
                .content(" memory ")
                .createdAt("now")
                .metadata(Map.of("turn_index", 3))
                .build();

        SemanticMemoryWriteCommand command = mapper.writeCommand(message, true);

        assertEquals("s1", command.sessionId());
        assertEquals("u1", command.userId());
        assertEquals("user", command.role());
        assertEquals(" memory ", command.content());
        assertEquals(3, command.metadata().get("turn_index"));
        assertEquals(true, command.embeddingAvailable());
    }

    @Test
    void mapsRetrievalConfigurationToTypedQuery() {
        SemanticMemoryRetrievalQuery query = mapper.retrievalQuery(
                " s1 ", " u1 ", " query ", 4, 8, true, false, 9D);

        assertEquals("s1", query.sessionId());
        assertEquals("u1", query.userId());
        assertEquals("query", query.query());
        assertEquals(4, query.limit());
        assertEquals(8, query.semanticTopK());
        assertEquals(true, query.embeddingAvailable());
        assertEquals(false, query.recencyAware());
        assertEquals(9D, query.recencyHalfLifeTurns());
    }

    @Test
    void mapsOnlyMessageDocumentsToHistoricalViews() {
        List<OpsMemoryMessage> views = mapper.messageViews(List.of(
                new SemanticMemoryDocumentSnapshot(
                        "doc-1",
                        "memory",
                        Map.of(
                                "memory_kind", "message",
                                "role", "assistant",
                                "created_at", "now")),
                new SemanticMemoryDocumentSnapshot(
                        "doc-2",
                        "summary",
                        Map.of("memory_kind", "summary"))),
                "s1",
                "u1");

        assertEquals(1, views.size());
        assertEquals("s1", views.get(0).getSessionId());
        assertEquals("u1", views.get(0).getUserId());
        assertEquals("assistant", views.get(0).getRole());
        assertEquals("memory", views.get(0).getContent());
        assertEquals("now", views.get(0).getCreatedAt());
    }

    @Test
    void nullInputsMapToSafeEmptyResults() {
        assertEquals(null, mapper.writeCommand(null, true));
        assertEquals(List.of(), mapper.messageViews(null, "s1", "u1"));
        assertEquals(List.of(), mapper.messageViews(List.of(), "s1", "u1"));
    }
}
