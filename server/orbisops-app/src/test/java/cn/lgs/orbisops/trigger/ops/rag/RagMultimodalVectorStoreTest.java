package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalVectorStoreTest {

    @Test
    void upsertMustValidateDimensionFormatVectorAndProjectRepositoryArguments() {
        FakeRepository repository = new FakeRepository();
        RagMultimodalVectorStore store = new RagMultimodalVectorStore(repository, settings(256));
        List<Double> embedding = java.util.stream.IntStream.range(0, 256)
                .mapToObj(index -> index == 0 ? 0.25d : 0.5d)
                .toList();

        store.upsert("doc-1", "content", Map.of("source", "manual.md"), embedding);

        assertEquals(1, repository.upsertCalls.get());
        assertEquals("table_name", repository.lastTableName);
        assertEquals(256, repository.lastDimension);
        assertEquals("doc-1", repository.lastId);
        assertEquals("content", repository.lastContent);
        assertEquals("manual.md", repository.lastMetadata.get("source"));
        assertTrue(repository.lastVectorLiteral.startsWith("[0.2500000000,0.5000000000"));
        assertTrue(repository.lastVectorLiteral.endsWith("]"));
    }

    @Test
    void upsertMustRejectNullAndWrongDimensionWithoutCallingRepository() {
        FakeRepository repository = new FakeRepository();
        RagMultimodalVectorStore store = new RagMultimodalVectorStore(repository, settings(256));

        IllegalStateException nullError = assertThrows(
                IllegalStateException.class,
                () -> store.upsert("id", "content", Map.of(), null));
        IllegalStateException wrongError = assertThrows(
                IllegalStateException.class,
                () -> store.upsert("id", "content", Map.of(), List.of(1d, 2d)));

        assertEquals("Embedding dimension mismatch, expected=256, actual=0", nullError.getMessage());
        assertEquals("Embedding dimension mismatch, expected=256, actual=2", wrongError.getMessage());
        assertEquals(0, repository.upsertCalls.get());
    }

    @Test
    void searchMustProjectSettingsFilterResolvedTopKAndDomainResults() {
        FakeRepository repository = new FakeRepository();
        repository.searchResults = List.of(new RagDocument("doc-1", "text", Map.of("source", "manual.md")));
        RagMultimodalVectorStore store = new RagMultimodalVectorStore(repository, settings(256));

        List<RagDocument> results = store.search(List.of(0.1d, 0.2d), "knowledge == 'ops'", 0);

        assertEquals(repository.searchResults, results);
        assertEquals(1, repository.searchCalls.get());
        assertEquals("table_name", repository.lastTableName);
        assertEquals(256, repository.lastDimension);
        assertEquals("[0.1000000000,0.2000000000]", repository.lastVectorLiteral);
        assertEquals("knowledge == 'ops'", repository.lastFilterExpression);
        assertEquals(8, repository.lastTopK);
        assertEquals("qwen-vl", repository.lastProvider);
        assertEquals("model", repository.lastModel);
    }

    @Test
    void vectorLiteralMustUseLocaleIndependentFixedTenDecimalFormat() {
        RagMultimodalVectorStore store = new RagMultimodalVectorStore(new FakeRepository(), settings(256));

        assertEquals("[]", store.vectorLiteral(null));
        assertEquals("[]", store.vectorLiteral(List.of()));
        assertEquals("[1.0000000000,-0.1250000000]", store.vectorLiteral(List.of(1d, -0.125d)));
    }

    private RagMultimodalSettings settings(int dimension) {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                dimension,
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
                1);
    }

    private static final class FakeRepository implements IRagMultimodalRepository {

        private final AtomicInteger upsertCalls = new AtomicInteger();
        private final AtomicInteger searchCalls = new AtomicInteger();
        private volatile List<RagDocument> searchResults = List.of();
        private volatile String lastTableName;
        private volatile int lastDimension;
        private volatile String lastId;
        private volatile String lastContent;
        private volatile Map<String, Object> lastMetadata = Map.of();
        private volatile String lastVectorLiteral;
        private volatile String lastFilterExpression;
        private volatile int lastTopK;
        private volatile String lastProvider;
        private volatile String lastModel;

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
            lastTableName = tableName;
            lastVectorLiteral = vectorLiteral;
            lastDimension = dimension;
            lastFilterExpression = filterExpression;
            lastTopK = topK;
            lastProvider = provider;
            lastModel = model;
            return searchResults;
        }

        @Override
        public void upsert(
                String tableName,
                int dimension,
                String id,
                String content,
                Map<String, Object> metadata,
                String vectorLiteral) {
            upsertCalls.incrementAndGet();
            lastTableName = tableName;
            lastDimension = dimension;
            lastId = id;
            lastContent = content;
            lastMetadata = metadata;
            lastVectorLiteral = vectorLiteral;
        }
    }
}
