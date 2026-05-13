package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalRetrievalCoordinatorTest {

    @Test
    void searchMustEmbedQueryRetrieveVectorResultsAndProjectSpringDocuments() throws Exception {
        FakeRepository repository = new FakeRepository();
        repository.results = List.of(
                new RagDocument("doc-1", "first", Map.of("source", "one.md")),
                new RagDocument("doc-2", "second", Map.of("source", "two.md")));
        RagMultimodalSettings settings = settings();
        AtomicReference<String> requestBody = new AtomicReference<>();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings,
                (uri, body, apiKey, timeout) -> {
                    requestBody.set(body);
                    return new RagMultimodalEmbeddingProtocol.HttpResult(
                            200, "{\"embeddings\":[[0.25,0.5]]}");
                },
                attempt -> {
                });
        RagMultimodalRetrievalCoordinator coordinator = new RagMultimodalRetrievalCoordinator(
                protocol,
                new RagMultimodalVectorStore(repository, settings));

        List<Document> documents = coordinator.search("latency", "knowledge == 'ops'", 5);

        assertEquals(2, documents.size());
        assertEquals("doc-1", documents.get(0).getId());
        assertEquals("first", documents.get(0).getText());
        assertEquals("one.md", documents.get(0).getMetadata().get("source"));
        assertEquals("doc-2", documents.get(1).getId());
        assertEquals("second", documents.get(1).getText());
        assertEquals("two.md", documents.get(1).getMetadata().get("source"));
        assertEquals(1, repository.searchCalls.get());
        assertEquals("[0.2500000000,0.5000000000]", repository.vectorLiteral);
        assertEquals("knowledge == 'ops'", repository.filterExpression);
        assertEquals(5, repository.topK);

        JSONObject payload = JSONObject.parseObject(requestBody.get());
        assertEquals("query", payload.getString("input_type"));
        assertEquals("latency", payload.getJSONArray("inputs")
                .getJSONObject(0)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text"));
    }

    @Test
    void protocolFailureMustPropagateWithoutRepositorySearch() {
        FakeRepository repository = new FakeRepository();
        RagMultimodalSettings settings = settings();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings,
                (uri, body, apiKey, timeout) -> {
                    throw new IOException("embedding unavailable");
                },
                attempt -> {
                });
        RagMultimodalRetrievalCoordinator coordinator = new RagMultimodalRetrievalCoordinator(
                protocol,
                new RagMultimodalVectorStore(repository, settings));

        IOException error = assertThrows(
                IOException.class,
                () -> coordinator.search("latency", null, 0));

        assertEquals("embedding unavailable", error.getMessage());
        assertEquals(0, repository.searchCalls.get());
    }

    @Test
    void emptyRepositoryResultMustProjectToEmptySpringDocumentList() throws Exception {
        FakeRepository repository = new FakeRepository();
        RagMultimodalSettings settings = settings();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings,
                (uri, body, apiKey, timeout) -> new RagMultimodalEmbeddingProtocol.HttpResult(
                        200, "{\"embeddings\":[[0.1]]}"),
                attempt -> {
                });
        RagMultimodalRetrievalCoordinator coordinator = new RagMultimodalRetrievalCoordinator(
                protocol,
                new RagMultimodalVectorStore(repository, settings));

        List<Document> documents = coordinator.search("query", null, 0);

        assertTrue(documents.isEmpty());
        assertEquals(1, repository.searchCalls.get());
        assertEquals(8, repository.topK);
    }

    private RagMultimodalSettings settings() {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                256,
                true,
                false,
                true,
                true,
                3,
                144,
                20_971_520L,
                3000,
                8,
                30,
                0);
    }

    private static final class FakeRepository implements IRagMultimodalRepository {

        private final AtomicInteger searchCalls = new AtomicInteger();
        private volatile List<RagDocument> results = List.of();
        private volatile String vectorLiteral;
        private volatile String filterExpression;
        private volatile int topK;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean ensureTable(String tableName, int dimension) {
            return true;
        }

        @Override
        public List<RagDocument> search(
                String tableName,
                String vectorLiteral,
                int dimension,
                String filterExpression,
                int topK,
                String provider,
                String model) {
            searchCalls.incrementAndGet();
            this.vectorLiteral = vectorLiteral;
            this.filterExpression = filterExpression;
            this.topK = topK;
            return results;
        }

        @Override
        public void upsert(
                String tableName,
                int dimension,
                String id,
                String content,
                Map<String, Object> metadata,
                String vectorLiteral) {
        }
    }
}
