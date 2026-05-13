package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagKnowledgeDocumentRecord;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagLexicalChunkRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagBm25LexicalRecallTest {

    @Test
    void shouldLoadCandidatesScoreBm25ProjectMetadataAndExcludeMemory() {
        RecordingRepository repository = new RecordingRepository(List.of(
                new RagLexicalChunkRecord(
                        "strong",
                        "锁单失败 锁单失败 ERR_LOCK_001 ERR_LOCK_001 分布式锁排查",
                        Map.of("knowledge", "demo-ops", "source", "runbook.md")),
                new RagLexicalChunkRecord(
                        "weak",
                        "锁单失败时检查库存",
                        Map.of("knowledge", "demo-ops", "source", "faq.md")),
                new RagLexicalChunkRecord(
                        "memory",
                        "锁单失败 ERR_LOCK_001 用户历史对话",
                        Map.of("knowledge", "ops-chat-memory", "memory_type", "ops_chat")),
                new RagLexicalChunkRecord(
                        "unmatched",
                        "支付回调签名校验",
                        Map.of("knowledge", "demo-ops"))));
        RagBm25LexicalRecall recall = new RagBm25LexicalRecall(repository);

        List<Document> documents = recall.search(
                "锁单失败 ERR_LOCK_001",
                "knowledge == 'demo-ops'",
                2);

        assertEquals("knowledge == 'demo-ops'", repository.filterExpression);
        assertEquals(100, repository.candidateLimit);
        assertFalse(repository.candidateTerms.isEmpty());
        assertEquals("err_lock_001", repository.candidateTerms.get(0));
        assertTrue(repository.candidateTerms.contains("锁单失败"));
        assertTrue(repository.candidateTerms.contains("锁单"));
        assertEquals(2, documents.size());
        assertEquals("strong", documents.get(0).getId());
        assertFalse(documents.stream().anyMatch(document -> "memory".equals(document.getId())));
        assertFalse(documents.stream().anyMatch(document -> "unmatched".equals(document.getId())));
        assertEquals("strong", documents.get(0).getMetadata().get("chunk_id"));
        assertEquals("bm25", documents.get(0).getMetadata().get("retrieval_source"));
        assertEquals("runbook.md", documents.get(0).getMetadata().get("source"));
        assertTrue(((Number) documents.get(0).getMetadata().get("bm25_score")).doubleValue() > 0d);
    }

    @Test
    void shouldScaleRepositoryCandidateLimitFromTopK() {
        RecordingRepository repository = new RecordingRepository(List.of(
                new RagLexicalChunkRecord("chunk", "traceid timeout", Map.of())));
        RagBm25LexicalRecall recall = new RagBm25LexicalRecall(repository);

        recall.search("traceid", null, 5);

        assertEquals(250, repository.candidateLimit);
    }

    @Test
    void shouldSkipRepositoryForBlankQuery() {
        RecordingRepository repository = new RecordingRepository(List.of());
        RagBm25LexicalRecall recall = new RagBm25LexicalRecall(repository);

        assertEquals(List.of(), recall.search("\u2003\t\n", null, 5));
        assertEquals(0, repository.searchCalls.get());
    }

    @Test
    void shouldReturnEmptyWhenRepositoryIsUnavailableOrMissing() {
        RecordingRepository unavailable = new RecordingRepository(List.of());
        unavailable.available = false;

        assertEquals(List.of(), new RagBm25LexicalRecall(unavailable).search("traceid", null, 5));
        assertEquals(List.of(), new RagBm25LexicalRecall(null).search("traceid", null, 5));
        assertEquals(0, unavailable.searchCalls.get());
    }

    @Test
    void shouldDegradeRepositoryFailureToEmptyResult() {
        RecordingRepository repository = new RecordingRepository(List.of());
        repository.failSearch = true;

        List<Document> documents = new RagBm25LexicalRecall(repository).search("traceid timeout", null, 5);

        assertEquals(List.of(), documents);
        assertEquals(1, repository.searchCalls.get());
    }

    @Test
    void shouldKeepTopKAndStableOrderForEqualScores() {
        RecordingRepository repository = new RecordingRepository(List.of(
                new RagLexicalChunkRecord("first", "timeout", Map.of()),
                new RagLexicalChunkRecord("second", "timeout", Map.of()),
                new RagLexicalChunkRecord("third", "timeout", Map.of())));

        List<Document> documents = new RagBm25LexicalRecall(repository).search("timeout", null, 2);

        assertEquals(List.of("first", "second"), documents.stream().map(Document::getId).toList());
    }

    private static final class RecordingRepository implements IRagKnowledgeRepository {

        private final List<RagLexicalChunkRecord> records;
        private final AtomicInteger searchCalls = new AtomicInteger();
        private boolean available = true;
        private boolean failSearch;
        private String filterExpression;
        private List<String> candidateTerms = List.of();
        private int candidateLimit;

        private RecordingRepository(List<RagLexicalChunkRecord> records) {
            this.records = records;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression,
                                                                   List<String> candidateTerms,
                                                                   int candidateLimit) {
            searchCalls.incrementAndGet();
            this.filterExpression = filterExpression;
            this.candidateTerms = List.copyOf(candidateTerms);
            this.candidateLimit = candidateLimit;
            if (failSearch) {
                throw new IllegalStateException("lexical repository unavailable");
            }
            return records;
        }

        @Override
        public List<RagDocument> searchVectorCandidates(String filterExpression,
                                                        String vectorLiteral,
                                                        int vectorDimensions,
                                                        int topK) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> listKnowledgeStats() {
            return List.of();
        }

        @Override
        public Map<String, Object> documentStats(String tag) {
            return Map.of();
        }

        @Override
        public boolean deleteChunk(String chunkId) {
            return false;
        }

        @Override
        public Map<String, Object> deleteChunksByTag(String tag) {
            return Map.of();
        }

        @Override
        public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
            return List.of();
        }

        @Override
        public RagKnowledgeDocumentRecord documentContent(String chunkId) {
            return null;
        }

        @Override
        public void persistParsedDocument(String name,
                                          String tag,
                                          String fileName,
                                          String contentType,
                                          long fileSize,
                                          List<RagDocument> documents) {
        }
    }
}
