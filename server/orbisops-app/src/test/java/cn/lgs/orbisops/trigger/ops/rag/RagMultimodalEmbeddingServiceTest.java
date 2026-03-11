package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalEmbeddingServiceTest {

    private final RagBinaryAssetPort binaryAssets = new NoOpBinaryAssetPort();

    @Test
    void legacyTwoArgumentConstructorMustRemainDisabledByDefault() {
        FakeRepository repository = new FakeRepository(true, true);
        RagMultimodalEmbeddingService service = service(
                repository, RagMultimodalSettings.defaults(), null);

        service.init();

        assertFalse(service.isSearchAvailable());
        assertEquals(0, repository.ensureCalls.get());
        assertEquals(0, repository.availableCalls.get());
    }

    @Test
    void autoInitMustInitializeOnceAndPublicAvailabilityMustReuseReadiness() {
        FakeRepository repository = new FakeRepository(true, true);
        RagMultimodalEmbeddingService service = service(repository, settings(true), null);

        service.init();

        assertTrue(service.isSearchAvailable());
        assertEquals(1, repository.ensureCalls.get());
        assertEquals("table_name", repository.lastTableName);
        assertEquals(2048, repository.lastDimension);
    }

    @Test
    void disabledAutoInitMustDeferInitializationUntilPublicAvailabilityCheck() {
        FakeRepository repository = new FakeRepository(true, true);
        RagMultimodalEmbeddingService service = service(
                repository, settings(false), null);

        service.init();
        assertEquals(0, repository.ensureCalls.get());

        assertTrue(service.isSearchAvailable());
        assertEquals(1, repository.ensureCalls.get());
    }

    @Test
    void repositoryUnavailableMustPreventTableInitialization() {
        FakeRepository repository = new FakeRepository(false, true);
        RagMultimodalEmbeddingService service = service(repository, settings(true), null);

        service.init();

        assertFalse(service.isSearchAvailable());
        assertEquals(0, repository.ensureCalls.get());
        assertTrue(repository.availableCalls.get() >= 1);
    }

    @Test
    void publicSearchMustDelegateEmbeddingProtocolAndPreserveRepositoryProjection() {
        FakeRepository repository = new FakeRepository(true, true);
        repository.searchResults = List.of(new RagDocument(
                "doc-1", "retrieved text", Map.of("source", "manual.md")));
        RagMultimodalSettings settings = settings(false);
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings,
                (uri, body, apiKey, timeout) -> new RagMultimodalEmbeddingProtocol.HttpResult(
                        200, "{\"embeddings\":[[0.25,0.5]]}"),
                attempt -> {
                });
        RagMultimodalEmbeddingService service = service(
                repository, settings, protocol);

        List<Document> documents = service.search("latency", "knowledge == 'ops'", 0);

        assertEquals(1, documents.size());
        assertEquals("doc-1", documents.get(0).getId());
        assertEquals("retrieved text", documents.get(0).getText());
        assertEquals("manual.md", documents.get(0).getMetadata().get("source"));
        assertEquals("table_name", repository.lastTableName);
        assertEquals(2048, repository.lastDimension);
        assertEquals("[0.2500000000,0.5000000000]", repository.lastVectorLiteral);
        assertEquals("knowledge == 'ops'", repository.lastFilterExpression);
        assertEquals(8, repository.lastTopK);
        assertEquals("qwen-vl", repository.lastProvider);
        assertEquals("model", repository.lastModel);
        assertEquals(1, repository.ensureCalls.get());
    }

    @Test
    void originalImageStoreMustConsumePreparedMediaAndPreserveUpsertProjection() {
        FakeRepository repository = new FakeRepository(true, true);
        RagMultimodalSettings settings = mediaSettings();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String responseBody = IntStream.range(0, 256)
                .mapToObj(index -> "0.5")
                .collect(Collectors.joining(",", "{\"embeddings\":[[", "]]}"));
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings,
                (uri, body, apiKey, timeout) -> {
                    requestBody.set(body);
                    return new RagMultimodalEmbeddingProtocol.HttpResult(200, responseBody);
                },
                attempt -> {
                });
        RagMultimodalEmbeddingService service = service(
                repository, settings, protocol);
        Document document = new Document(
                "doc-image",
                "Image caption",
                Map.of("source", "sample.png"));

        service.storeDocuments(
                List.of(document),
                new InMemoryRagFileResource("sample.png", "image/png", new byte[]{1, 2, 3}));

        assertEquals(1, repository.upsertCalls.get());
        assertTrue(repository.lastUpsertId.startsWith("doc-image:mm:image:1:"));
        assertTrue(repository.lastUpsertContent.contains("Image caption"));
        assertEquals("image", repository.lastUpsertMetadata.get("multimodal_media_type"));
        assertEquals("image/png", repository.lastUpsertMetadata.get("multimodal_image_mime_type"));
        assertEquals(256, repository.lastDimension);
        assertTrue(repository.lastUpsertVector.startsWith("[0.5000000000,0.5000000000"));
        assertTrue(requestBody.get().contains("data:image/png;base64,AQID"));
        assertEquals(1, repository.ensureCalls.get());
    }

    private RagMultimodalEmbeddingService service(
            IRagMultimodalRepository repository,
            RagMultimodalSettings settings,
            RagMultimodalEmbeddingProtocol protocol) {
        RagMultimodalRuntimeComponents components = protocol == null
                ? RagMultimodalRuntimeFactory.create(repository, settings)
                : RagMultimodalRuntimeFactory.create(
                        repository,
                        settings,
                        protocol,
                        new RagMultimodalMediaPreparer(settings));
        return new RagMultimodalEmbeddingService(binaryAssets, components);
    }

    private RagMultimodalSettings mediaSettings() {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                256,
                false,
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

    private RagMultimodalSettings settings(boolean autoInit) {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                2048,
                autoInit,
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

        private final boolean available;
        private final boolean ensureResult;
        private final AtomicInteger availableCalls = new AtomicInteger();
        private final AtomicInteger ensureCalls = new AtomicInteger();
        private final AtomicInteger upsertCalls = new AtomicInteger();
        private volatile List<RagDocument> searchResults = List.of();
        private volatile String lastTableName;
        private volatile int lastDimension;
        private volatile String lastVectorLiteral;
        private volatile String lastFilterExpression;
        private volatile int lastTopK;
        private volatile String lastProvider;
        private volatile String lastModel;
        private volatile String lastUpsertId;
        private volatile String lastUpsertContent;
        private volatile Map<String, Object> lastUpsertMetadata = Map.of();
        private volatile String lastUpsertVector;

        private FakeRepository(boolean available, boolean ensureResult) {
            this.available = available;
            this.ensureResult = ensureResult;
        }

        @Override
        public boolean available() {
            availableCalls.incrementAndGet();
            return available;
        }

        @Override
        public boolean ensureTable(String tableName, int dimension) {
            ensureCalls.incrementAndGet();
            lastTableName = tableName;
            lastDimension = dimension;
            return ensureResult;
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
            lastUpsertId = id;
            lastUpsertContent = content;
            lastUpsertMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            lastUpsertVector = vectorLiteral;
        }
    }

    private static final class InMemoryRagFileResource implements RagFileResource {

        private final String fileName;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryRagFileResource(String fileName, String contentType, byte[] bytes) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String name() {
            return fileName;
        }

        @Override
        public String originalFilename() {
            return fileName;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }

    private static final class NoOpBinaryAssetPort implements RagBinaryAssetPort {

        @Override
        public Path store(String knowledge, String source, String fileName, byte[] content) throws IOException {
            return Path.of(fileName == null ? "asset.bin" : fileName);
        }

        @Override
        public boolean isRegularFile(Path path) {
            return false;
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return new byte[0];
        }
    }
}
