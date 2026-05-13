package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeChunkDeletionOutcome;
import cn.lgs.orbisops.application.knowledge.KnowledgeDocumentStatistics;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagChunk;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagKnowledgeDocumentRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsKnowledgeRagDocumentAdapterTest {

    @Test
    void mapsRepositoryMetadataToTypedListAndFullContent() {
        IRagKnowledgeRepository repository = mock(IRagKnowledgeRepository.class);
        OpsKnowledgeRagDocumentAdapter adapter = new OpsKnowledgeRagDocumentAdapter(repository);
        RagKnowledgeDocumentRecord record = new RagKnowledgeDocumentRecord(
                "chunk-1",
                "full content",
                "{\"knowledge\":\"ops\",\"source\":\"runbook.md\",\"document_type\":\"markdown\",\"chunk_index\":2,\"chunk_strategy\":\"section\"}");
        when(repository.listDocuments("ops", "GLOBAL", "")).thenReturn(List.of(record));
        when(repository.documentContent("chunk-1")).thenReturn(record);

        List<KnowledgeRagChunk> listed = adapter.list("ops", "GLOBAL", "");
        KnowledgeRagChunk content = adapter.content("chunk-1");

        assertEquals(1, listed.size());
        assertEquals("", listed.get(0).content());
        assertEquals("runbook.md", listed.get(0).displayName());
        assertEquals("markdown", listed.get(0).documentType());
        assertEquals(2, listed.get(0).chunkIndex());
        assertEquals("full content", content.content());
        assertEquals(12L, content.size());
    }

    @Test
    void mapsStatisticsDeletionAndLegacyUnscopedDelete() {
        IRagKnowledgeRepository repository = mock(IRagKnowledgeRepository.class);
        OpsKnowledgeRagDocumentAdapter adapter = new OpsKnowledgeRagDocumentAdapter(repository);
        when(repository.documentStats("ops", "PROJECT", "project-1")).thenReturn(Map.of(
                "chunkCount", 5L,
                "documentCount", 2L,
                "byType", List.of(Map.of("key", "markdown", "count", 5L)),
                "bySource", List.of(Map.of("key", "runbook.md", "count", 5L)),
                "tag", "ops",
                "scope", "PROJECT",
                "projectId", "project-1"));
        when(repository.deleteChunksByTag("ops", "PROJECT", "project-1")).thenReturn(Map.of(
                "knowledgeTag", "ops",
                "scope", "PROJECT",
                "projectId", "project-1",
                "deletedChunks", 5L));
        when(repository.deleteChunk("chunk-1")).thenReturn(true);

        KnowledgeDocumentStatistics statistics = adapter.statistics("ops", "PROJECT", "project-1");
        KnowledgeChunkDeletionOutcome deletion = adapter.deleteChunks("ops", "PROJECT", "project-1");

        assertEquals(5L, statistics.chunkCount());
        assertEquals(2L, statistics.documentCount());
        assertEquals("markdown", statistics.byType().get(0).key());
        assertEquals(5L, deletion.deletedChunks());
        assertTrue(adapter.deleteChunk("chunk-1"));
        verify(repository).deleteChunk("chunk-1");
    }
}
