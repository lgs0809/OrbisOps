package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalVectorStoreBoundaryArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/";
    private static final String SERVICE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String VECTOR_STORE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalVectorStore.java";

    @Test
    void vectorStoreMustOwnDimensionValidationLiteralAndRepositoryProjection() throws IOException {
        String vectorStore = read(VECTOR_STORE);

        assertAll(
                () -> assertTrue(vectorStore.contains("class RagMultimodalVectorStore")),
                () -> assertTrue(vectorStore.contains("IRagMultimodalRepository repository")),
                () -> assertTrue(vectorStore.contains("public void upsert(")),
                () -> assertTrue(vectorStore.contains("public List<RagDocument> search(")),
                () -> assertTrue(vectorStore.contains("validateWriteDimension(embedding)")),
                () -> assertTrue(vectorStore.contains("Embedding dimension mismatch")),
                () -> assertTrue(vectorStore.contains("vectorLiteral(embedding)")),
                () -> assertTrue(vectorStore.contains("String.format(Locale.ROOT, \"%.10f\"")),
                () -> assertTrue(vectorStore.contains("repository.upsert(")),
                () -> assertTrue(vectorStore.contains("repository.search(")),
                () -> assertTrue(vectorStore.contains("settings.resolveSearchTopK(requestedTopK)")),
                () -> assertTrue(vectorStore.contains("settings.provider()")),
                () -> assertTrue(vectorStore.contains("settings.model()")));
    }

    @Test
    void vectorStoreMustNotAbsorbEmbeddingMediaProjectionOrSpringMapping() throws IOException {
        String vectorStore = read(VECTOR_STORE);

        assertAll(
                () -> assertFalse(vectorStore.contains("RagMultimodalEmbeddingProtocol")),
                () -> assertFalse(vectorStore.contains("RagMultimodalMediaPreparer")),
                () -> assertFalse(vectorStore.contains("RagMultimodalIngestionProjector")),
                () -> assertFalse(vectorStore.contains("RagBinaryAssetPort")),
                () -> assertFalse(vectorStore.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(vectorStore.contains("PDFRenderer")),
                () -> assertFalse(vectorStore.contains("ImageIO")),
                () -> assertFalse(vectorStore.contains("multimodalMetadata")),
                () -> assertFalse(vectorStore.contains("sha256")),
                () -> assertFalse(vectorStore.contains("springDocument")));
    }

    @Test
    void serviceMustDelegateVectorOperationsWithoutDirectRepositoryProjection() throws IOException {
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(service.contains("RagMultimodalVectorStore vectorStore")),
                () -> assertTrue(service.contains("vectorStore.upsert(")),
                () -> assertTrue(service.contains("this.vectorStore = components.vectorStore()")),
                () -> assertFalse(service.contains("new RagMultimodalVectorStore(")),
                () -> assertFalse(service.contains("IRagMultimodalRepository")),
                () -> assertFalse(service.contains("multimodalRepository.search(")),
                () -> assertFalse(service.contains("multimodalRepository.upsert(")),
                () -> assertFalse(service.contains("vectorLiteral(")),
                () -> assertFalse(service.contains("Embedding dimension mismatch")),
                () -> assertFalse(service.contains("String.format(Locale.ROOT")),
                () -> assertTrue(service.lines().count() <= 260));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
