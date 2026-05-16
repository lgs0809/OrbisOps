package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcKnowledgeDocumentCatalogRepositoryTest {

    @Test
    void missingJdbcKeepsBestEffortCatalogWritesNonBlocking() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcKnowledgeDocumentCatalogRepository repository =
                new JdbcKnowledgeDocumentCatalogRepository(provider);

        assertDoesNotThrow(() -> repository.saveSubmitted(List.of(document())));
        assertDoesNotThrow(() -> repository.synchronize(document(), List.of(chunk())));
    }

    @Test
    void submittedDocumentsUseSubmittedUpsertContract() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcKnowledgeDocumentCatalogRepository repository =
                new JdbcKnowledgeDocumentCatalogRepository(provider);

        repository.saveSubmitted(List.of(document()));

        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_knowledge_document")
                                && sql.contains("ingestion_job_id=VALUES(ingestion_job_id)")
                                && !sql.contains("chunk_count=VALUES(chunk_count)")),
                any(Object[].class));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_knowledge_chunk")),
                any(Object[].class));
    }

    @Test
    void parsedSynchronizationWritesDocumentAndChunks() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcKnowledgeDocumentCatalogRepository repository =
                new JdbcKnowledgeDocumentCatalogRepository(provider);

        repository.synchronize(document(), List.of(chunk()));

        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_knowledge_document")
                                && sql.contains("chunk_count=VALUES(chunk_count)")),
                any(Object[].class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_knowledge_chunk")
                                && sql.contains("content_preview=VALUES(content_preview)")),
                any(Object[].class));
    }

    private KnowledgeDocumentCatalogEntry document() {
        return new KnowledgeDocumentCatalogEntry(
                "doc-1",
                key(),
                "runbook.md",
                "Runbook",
                "STRUCTURED_RAG",
                "md",
                128L,
                "READY",
                "VECTOR_PIPELINE",
                1,
                true,
                "rag-job-1",
                Map.of("structurePreserved", true));
    }

    private KnowledgeChunkCatalogEntry chunk() {
        return new KnowledgeChunkCatalogEntry(
                "chunk-1",
                "doc-1",
                key(),
                0,
                "STRUCTURE_FIRST",
                "preview",
                "READY",
                "VECTOR_PIPELINE",
                Map.of("previewable", true));
    }

    private KnowledgeBaseCatalogKey key() {
        return new KnowledgeBaseCatalogKey(KnowledgeScope.GLOBAL, "", "ops-kb");
    }
}
