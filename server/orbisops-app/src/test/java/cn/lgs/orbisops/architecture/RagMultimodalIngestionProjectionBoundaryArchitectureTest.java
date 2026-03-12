package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalIngestionProjectionBoundaryArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/";
    private static final String SERVICE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String PROJECTOR = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalIngestionProjector.java";

    @Test
    void projectorMustOwnTextAndMediaIngestionProjection() throws IOException {
        String projector = read(PROJECTOR);

        assertAll(
                () -> assertTrue(projector.contains("class RagMultimodalIngestionProjector")),
                () -> assertTrue(projector.contains("projectText(Document document)")),
                () -> assertTrue(projector.contains("projectMedia(")),
                () -> assertTrue(projector.contains("representativeDocument(")),
                () -> assertTrue(projector.contains("mediaContent(")),
                () -> assertTrue(projector.contains("multimodalMetadata(")),
                () -> assertTrue(projector.contains("sha256(")),
                () -> assertTrue(projector.contains("MessageDigest.getInstance(\"SHA-256\")")),
                () -> assertTrue(projector.contains("HexFormat.of()")),
                () -> assertTrue(projector.contains("document.getId() + \":mm:text\"")),
                () -> assertTrue(projector.contains("multimodal_image_sha256")),
                () -> assertTrue(projector.contains("record TextProjection")),
                () -> assertTrue(projector.contains("record MediaProjection")),
                () -> assertTrue(projector.contains("Collections.unmodifiableMap")),
                () -> assertTrue(projector.contains("return bytes.clone()")));
    }

    @Test
    void projectorMustNotAbsorbMediaPreparationEmbeddingOrPersistence() throws IOException {
        String projector = read(PROJECTOR);

        assertAll(
                () -> assertFalse(projector.contains("PDFRenderer")),
                () -> assertFalse(projector.contains("ImageIO")),
                () -> assertFalse(projector.contains("RagFileResource")),
                () -> assertFalse(projector.contains("RagBinaryAssetPort")),
                () -> assertFalse(projector.contains("IRagMultimodalRepository")),
                () -> assertFalse(projector.contains("RagMultimodalEmbeddingProtocol")),
                () -> assertFalse(projector.contains("RagMultimodalVectorStore")),
                () -> assertFalse(projector.contains("repository.upsert(")),
                () -> assertFalse(projector.contains("repository.search(")),
                () -> assertFalse(projector.contains("vectorLiteral(")));
    }

    @Test
    void serviceMustDelegateProjectionWithoutRetainingProjectionRules() throws IOException {
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(service.contains("RagMultimodalIngestionProjector ingestionProjector")),
                () -> assertTrue(service.contains(".projectText(document)")),
                () -> assertTrue(service.contains("ingestionProjector.projectMedia(documents, media)")),
                () -> assertTrue(service.contains("ingestionProjector.pageNumber(")),
                () -> assertFalse(service.contains("representativeDocument(")),
                () -> assertFalse(service.contains("mediaContent(")),
                () -> assertFalse(service.contains("multimodalMetadata(")),
                () -> assertFalse(service.contains("sha256(")),
                () -> assertFalse(service.contains("MessageDigest")),
                () -> assertFalse(service.contains("HexFormat")),
                () -> assertFalse(service.contains("multimodal_image_sha256")),
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
