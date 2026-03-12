package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalRetrievalCoordinationBoundaryArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/";
    private static final String SERVICE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String COORDINATOR = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalRetrievalCoordinator.java";

    @Test
    void coordinatorMustOwnQueryEmbeddingVectorSearchAndSpringProjection() throws IOException {
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertTrue(coordinator.contains("class RagMultimodalRetrievalCoordinator")),
                () -> assertTrue(coordinator.contains("RagMultimodalEmbeddingProtocol embeddingProtocol")),
                () -> assertTrue(coordinator.contains("RagMultimodalVectorStore vectorStore")),
                () -> assertTrue(coordinator.contains("embeddingProtocol.embedText(query, \"query\")")),
                () -> assertTrue(coordinator.contains("vectorStore.search(embedding, filterExpression, requestedTopK)")),
                () -> assertTrue(coordinator.contains("map(this::springDocument)")),
                () -> assertTrue(coordinator.contains("new Document(document.id(), document.text(), document.metadata())")));
    }

    @Test
    void coordinatorMustRemainFreeOfAvailabilityMediaIngestionAndDirectRepositoryMechanics() throws IOException {
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertFalse(coordinator.contains("RagMultimodalAvailability")),
                () -> assertFalse(coordinator.contains("RagMultimodalTableReadiness")),
                () -> assertFalse(coordinator.contains("RagMultimodalMediaPreparer")),
                () -> assertFalse(coordinator.contains("RagMultimodalIngestionProjector")),
                () -> assertFalse(coordinator.contains("RagBinaryAssetPort")),
                () -> assertFalse(coordinator.contains("IRagMultimodalRepository")),
                () -> assertFalse(coordinator.contains("repository.search(")),
                () -> assertFalse(coordinator.contains("repository.upsert(")),
                () -> assertFalse(coordinator.contains("vectorLiteral(")),
                () -> assertFalse(coordinator.contains("@Service")),
                () -> assertFalse(coordinator.contains("@Slf4j")));
    }

    @Test
    void serviceMustDelegateRetrievalAndRetainOnlyAvailabilityGuardAndFallback() throws IOException {
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(service.contains("RagMultimodalRetrievalCoordinator retrievalCoordinator")),
                () -> assertTrue(service.contains("retrievalCoordinator.search(query, filterExpression, topK)")),
                () -> assertTrue(service.contains("!StringUtils.hasText(query) || !isSearchAvailable()")),
                () -> assertTrue(service.contains("多模态向量检索失败，降级为空结果")),
                () -> assertFalse(service.contains("embeddingProtocol.embedText(query, \"query\")")),
                () -> assertFalse(service.contains("vectorStore.search(embedding")),
                () -> assertFalse(service.contains("springDocument(")),
                () -> assertFalse(service.contains("RagDocument")),
                () -> assertFalse(service.contains("new Document(document.id()")),
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
