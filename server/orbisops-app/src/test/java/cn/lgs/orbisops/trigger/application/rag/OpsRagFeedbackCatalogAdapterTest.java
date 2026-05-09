package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackEntry;
import cn.lgs.orbisops.application.rag.RagFeedbackSubmitCommand;
import cn.lgs.orbisops.application.rag.RagKnowledgeGap;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagFeedbackRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRagFeedbackCatalogAdapterTest {

    @Test
    void serializesTypedFeedbackCommandForLegacyRepository() {
        IRagFeedbackRepository repository = mock(IRagFeedbackRepository.class);
        OpsRagFeedbackCatalogAdapter adapter = new OpsRagFeedbackCatalogAdapter(repository);
        RagFeedbackSubmitCommand command = new RagFeedbackSubmitCommand(
                "问题", "答案", false, true, "chat", "message-1", "ops",
                List.of("chunk-1", "chunk-2"), "说明");
        when(repository.insertFeedback(
                "问题", "答案", false, true, "chat", "message-1", "ops",
                "[\"chunk-1\",\"chunk-2\"]", "说明")).thenReturn(7L);

        assertEquals(7L, adapter.insertFeedback(command));

        verify(repository).insertFeedback(
                "问题", "答案", false, true, "chat", "message-1", "ops",
                "[\"chunk-1\",\"chunk-2\"]", "说明");
    }

    @Test
    void mapsRepositoryRowsToTypedFeedbackAndGapRecords() {
        IRagFeedbackRepository repository = mock(IRagFeedbackRepository.class);
        OpsRagFeedbackCatalogAdapter adapter = new OpsRagFeedbackCatalogAdapter(repository);
        when(repository.listFeedback("ops", false, null, 100)).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 7L),
                Map.entry("queryText", "问题"),
                Map.entry("answerText", "答案"),
                Map.entry("useful", 0),
                Map.entry("resolved", 1),
                Map.entry("sourceType", "chat"),
                Map.entry("sourceId", "message-1"),
                Map.entry("knowledgeTag", "ops"),
                Map.entry("chunkIdsJson", "[\"chunk-1\",\"chunk-2\"]"),
                Map.entry("commentText", "说明"),
                Map.entry("createTime", "2026-07-30 10:00:00"))));
        when(repository.listGaps("OPEN", "ops", 100)).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 9L),
                Map.entry("gapKey", "gap-key"),
                Map.entry("queryText", "问题"),
                Map.entry("knowledgeTag", "ops"),
                Map.entry("status", "OPEN"),
                Map.entry("feedbackCount", 2),
                Map.entry("sampleComment", "说明"),
                Map.entry("lastFeedbackAt", "2026-07-30 10:00:00"),
                Map.entry("createTime", "2026-07-30 09:00:00"),
                Map.entry("updateTime", "2026-07-30 10:00:00"))));

        RagFeedbackEntry feedback = adapter.listFeedback("ops", false, null, 100).get(0);
        RagKnowledgeGap gap = adapter.listGaps("OPEN", "ops", 100).get(0);

        assertEquals(7L, feedback.id());
        assertFalse(feedback.useful());
        assertTrue(feedback.resolved());
        assertEquals(List.of("chunk-1", "chunk-2"), feedback.chunkIds());
        assertEquals(9L, gap.id());
        assertEquals("gap-key", gap.gapKey());
        assertEquals(2, gap.feedbackCount());
    }

    @Test
    void emptyGapRowBecomesAbsentAndMalformedChunkJsonDegradesToEmptyList() {
        IRagFeedbackRepository repository = mock(IRagFeedbackRepository.class);
        OpsRagFeedbackCatalogAdapter adapter = new OpsRagFeedbackCatalogAdapter(repository);
        when(repository.queryGap(404L)).thenReturn(Map.of());
        when(repository.listFeedback("", null, null, 1)).thenReturn(List.of(Map.of(
                "id", 1L,
                "queryText", "问题",
                "chunkIdsJson", "not-json")));

        assertNull(adapter.findGap(404L));
        assertEquals(List.of(), adapter.listFeedback("", null, null, 1).get(0).chunkIds());
    }
}
