package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalEmbeddingProtocolBoundaryArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/";
    private static final String SERVICE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String PROTOCOL = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingProtocol.java";

    @Test
    void protocolMustOwnPayloadTransportResponseParsingAndRetry() throws IOException {
        String protocol = read(PROTOCOL);

        assertAll(
                () -> assertTrue(protocol.contains("class RagMultimodalEmbeddingProtocol")),
                () -> assertTrue(protocol.contains("public List<Double> embedText")),
                () -> assertTrue(protocol.contains("public List<Double> embedImage")),
                () -> assertTrue(protocol.contains("body.put(\"inputs\"")),
                () -> assertTrue(protocol.contains("body.put(\"model\", settings.model())")),
                () -> assertTrue(protocol.contains("body.put(\"input_type\"")),
                () -> assertTrue(protocol.contains("body.put(\"truncation\", true)")),
                () -> assertTrue(protocol.contains("body.put(\"output_dimension\", settings.dimension())")),
                () -> assertTrue(protocol.contains("image.put(\"image_base64\"")),
                () -> assertTrue(protocol.contains("HttpClient.newBuilder()")),
                () -> assertTrue(protocol.contains("HttpClient.Version.HTTP_1_1")),
                () -> assertTrue(protocol.contains("Authorization")),
                () -> assertTrue(protocol.contains("settings.timeoutSeconds()")),
                () -> assertTrue(protocol.contains("settings.maxRetries() + 1")),
                () -> assertTrue(protocol.contains("retryable(response.statusCode())")),
                () -> assertTrue(protocol.contains("retryDelay.pause(attempt)")),
                () -> assertTrue(protocol.contains("response.getJSONArray(\"embeddings\")")),
                () -> assertTrue(protocol.contains("response.getJSONArray(\"data\")")),
                () -> assertTrue(protocol.contains("Multimodal embedding response does not contain embeddings")),
                () -> assertTrue(protocol.contains("interface HttpTransport")),
                () -> assertTrue(protocol.contains("interface RetryDelay")));
    }

    @Test
    void protocolMustRemainFreeOfMediaRepositoryAndSpringDocumentResponsibilities() throws IOException {
        String protocol = read(PROTOCOL);

        assertAll(
                () -> assertFalse(protocol.contains("PDFRenderer")),
                () -> assertFalse(protocol.contains("PDDocument")),
                () -> assertFalse(protocol.contains("ImageIO")),
                () -> assertFalse(protocol.contains("BufferedImage")),
                () -> assertFalse(protocol.contains("RagFileResource")),
                () -> assertFalse(protocol.contains("RagBinaryAssetPort")),
                () -> assertFalse(protocol.contains("IRagMultimodalRepository")),
                () -> assertFalse(protocol.contains("multimodalRepository")),
                () -> assertFalse(protocol.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(protocol.contains("vectorLiteral")),
                () -> assertFalse(protocol.contains("upsert(")),
                () -> assertFalse(protocol.contains("springDocument")));
    }

    @Test
    void serviceMustDelegateProtocolAndRetainMediaAndPersistenceProjection() throws IOException {
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(service.contains("RagMultimodalEmbeddingProtocol embeddingProtocol")),
                () -> assertFalse(service.contains("embeddingProtocol.embedText(query, \"query\")")),
                () -> assertTrue(service.contains("embeddingProtocol.embedText(projection.content(), \"document\")")),
                () -> assertTrue(service.contains("embeddingProtocol.embedImage(")),
                () -> assertTrue(service.contains("RagMultimodalRetrievalCoordinator retrievalCoordinator")),
                () -> assertTrue(service.contains("this.embeddingProtocol = components.embeddingProtocol()")),
                () -> assertFalse(service.contains("new RagMultimodalEmbeddingProtocol(")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("HttpClient")),
                () -> assertFalse(service.contains("HttpRequest")),
                () -> assertFalse(service.contains("HttpResponse")),
                () -> assertFalse(service.contains("Base64")),
                () -> assertFalse(service.contains("private JSONObject postJson")),
                () -> assertFalse(service.contains("parseEmbedding(")),
                () -> assertFalse(service.contains("retryable(")),
                () -> assertFalse(service.contains("sleepBeforeRetry(")),
                () -> assertTrue(service.contains("RagMultimodalMediaPreparer mediaPreparer")),
                () -> assertTrue(service.contains("RagMultimodalVectorStore vectorStore")),
                () -> assertFalse(service.contains("PDFRenderer")),
                () -> assertFalse(service.contains("ImageIO")),
                () -> assertFalse(service.contains("multimodalRepository.search(")),
                () -> assertFalse(service.contains("multimodalRepository.upsert(")),
                () -> assertFalse(service.contains("vectorLiteral(")),
                () -> assertFalse(service.contains("springDocument(")),
                () -> assertTrue(service.contains("retrievalCoordinator.search(")),
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
