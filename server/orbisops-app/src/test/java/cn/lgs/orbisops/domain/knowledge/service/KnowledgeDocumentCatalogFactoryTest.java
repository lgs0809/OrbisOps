package cn.lgs.orbisops.domain.knowledge.service;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KnowledgeDocumentCatalogFactoryTest {

    private final KnowledgeDocumentCatalogFactory factory = new KnowledgeDocumentCatalogFactory();

    @Test
    void documentIdIsDeterministicAndBoundToKnowledgeScope() {
        KnowledgeBaseCatalogKey global = new KnowledgeBaseCatalogKey(
                KnowledgeScope.GLOBAL, "", "ops-kb");
        KnowledgeBaseCatalogKey project = new KnowledgeBaseCatalogKey(
                KnowledgeScope.PROJECT, "project-1", "ops-kb");

        String first = factory.documentId(global, "runbook.md");
        String second = factory.documentId(global, "runbook.md");
        String projectDocument = factory.documentId(project, "runbook.md");

        assertEquals(first, second);
        assertNotEquals(first, projectDocument);
    }

    @Test
    void submittedDocumentExtractsExtensionAndJobMetadata() {
        KnowledgeBaseCatalogKey key = new KnowledgeBaseCatalogKey(
                KnowledgeScope.GLOBAL, "", "ops-kb");

        KnowledgeDocumentCatalogEntry document = factory.submitted(
                key, "Runbook.MD", 128L, "rag-job-1", "PENDING");

        assertEquals("md", document.documentType());
        assertEquals("SUBMITTED", document.parseStatus());
        assertEquals("ASYNC", document.vectorStatus());
        assertEquals("rag-job-1", document.ingestionJobId());
        assertEquals("PENDING", document.metadata().get("jobStatus"));
    }

    @Test
    void parsedChunkUsesFallbackIdAndTruncatesPreview() {
        KnowledgeBaseCatalogKey key = new KnowledgeBaseCatalogKey(
                KnowledgeScope.PROJECT, "project-1", "ops-kb");
        String content = "x".repeat(1200);

        KnowledgeChunkCatalogEntry chunk = factory.parsedChunk(
                key, "doc-1", "", 3, "STRUCTURE", content,
                "", "", "runbook.md", true);

        assertEquals("doc-1:3", chunk.chunkId());
        assertEquals(1000, chunk.contentPreview().length());
        assertEquals("READY", chunk.parseStatus());
        assertEquals("VECTOR_PIPELINE", chunk.vectorStatus());
        assertEquals(true, chunk.metadata().get("previewable"));
    }
}
