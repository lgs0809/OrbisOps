package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeDocumentCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDocumentCatalogApplicationServiceTest {

    @Test
    void recordsSubmittedDocumentsAsSingleBatch() {
        RecordingRepository repository = new RecordingRepository();
        KnowledgeDocumentCatalogApplicationService service =
                new KnowledgeDocumentCatalogApplicationService(repository);

        service.recordSubmitted(
                "GLOBAL",
                "ignored",
                "ops-kb",
                List.of(
                        new KnowledgeUploadDocument("runbook.md", 128L),
                        new KnowledgeUploadDocument("incident.pdf", 256L)),
                "rag-job-1",
                "PENDING");

        assertEquals(2, repository.submitted.size());
        assertEquals("", repository.submitted.get(0).key().projectId());
        assertEquals("rag-job-1", repository.submitted.get(0).ingestionJobId());
        assertEquals("pdf", repository.submitted.get(1).documentType());
    }

    @Test
    void groupsParsedChunksByFileAndSynchronizesEachDocument() {
        RecordingRepository repository = new RecordingRepository();
        KnowledgeDocumentCatalogApplicationService service =
                new KnowledgeDocumentCatalogApplicationService(repository);

        service.synchronizeParsed(
                "PROJECT",
                "project-1",
                "ops-kb",
                List.of(
                        chunk("a-1", "a.md", 0),
                        chunk("a-2", "a.md", 1),
                        chunk("", "b.md", 0)));

        assertEquals(2, repository.synchronizedDocuments.size());
        assertEquals(2, repository.synchronizedChunks.get(0).size());
        assertEquals(1, repository.synchronizedChunks.get(1).size());
        KnowledgeChunkCatalogEntry fallback = repository.synchronizedChunks.get(1).get(0);
        assertEquals(repository.synchronizedDocuments.get(1).documentId() + ":0", fallback.chunkId());
    }

    @Test
    void rejectsProjectSynchronizationWithoutProjectId() {
        KnowledgeDocumentCatalogApplicationService service =
                new KnowledgeDocumentCatalogApplicationService(new RecordingRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.synchronizeParsed(
                        "PROJECT", "", "ops-kb", List.of(chunk("c-1", "a.md", 0))));
    }

    private KnowledgeParsedChunk chunk(String chunkId, String fileName, int index) {
        return new KnowledgeParsedChunk(
                chunkId,
                fileName,
                fileName,
                fileName,
                "md",
                index,
                "STRUCTURE_FIRST",
                100L,
                "content-" + index,
                "READY",
                "VECTOR_PIPELINE",
                true);
    }

    private static final class RecordingRepository
            implements IKnowledgeDocumentCatalogRepository {
        private List<KnowledgeDocumentCatalogEntry> submitted = List.of();
        private final List<KnowledgeDocumentCatalogEntry> synchronizedDocuments = new ArrayList<>();
        private final List<List<KnowledgeChunkCatalogEntry>> synchronizedChunks = new ArrayList<>();

        @Override
        public void saveSubmitted(List<KnowledgeDocumentCatalogEntry> documents) {
            submitted = List.copyOf(documents);
        }

        @Override
        public void synchronize(KnowledgeDocumentCatalogEntry document,
                                List<KnowledgeChunkCatalogEntry> chunks) {
            synchronizedDocuments.add(document);
            synchronizedChunks.add(List.copyOf(chunks));
        }
    }
}
