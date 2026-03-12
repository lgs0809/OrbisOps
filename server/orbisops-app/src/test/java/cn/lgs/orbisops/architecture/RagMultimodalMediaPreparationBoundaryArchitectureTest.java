package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalMediaPreparationBoundaryArchitectureTest {

    private static final String ROOT = "orbisops-trigger/src/main/java/";
    private static final String SERVICE = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String PREPARER = ROOT
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalMediaPreparer.java";

    @Test
    void mediaPreparerMustOwnClassificationDecodeConversionEligibilityAndPdfRendering() throws IOException {
        String preparer = read(PREPARER);

        assertAll(
                () -> assertTrue(preparer.contains("class RagMultimodalMediaPreparer")),
                () -> assertTrue(preparer.contains("RagFileResource")),
                () -> assertTrue(preparer.contains("IMAGE_EXTENSIONS")),
                () -> assertTrue(preparer.contains("normalizeMimeType")),
                () -> assertTrue(preparer.contains("normalizeImageMimeType")),
                () -> assertTrue(preparer.contains("extension(")),
                () -> assertTrue(preparer.contains("ImageIO.read")),
                () -> assertTrue(preparer.contains("ImageIO.write")),
                () -> assertTrue(preparer.contains("ByteArrayInputStream")),
                () -> assertTrue(preparer.contains("ByteArrayOutputStream")),
                () -> assertTrue(preparer.contains("Loader.loadPDF")),
                () -> assertTrue(preparer.contains("PDDocument")),
                () -> assertTrue(preparer.contains("PDFRenderer")),
                () -> assertTrue(preparer.contains("renderImageWithDPI")),
                () -> assertTrue(preparer.contains("settings.indexPdfPageImages()")),
                () -> assertTrue(preparer.contains("settings.maxPdfPages()")),
                () -> assertTrue(preparer.contains("settings.pdfRenderDpi()")),
                () -> assertTrue(preparer.contains("settings.maxImageBytes()")),
                () -> assertTrue(preparer.contains("settings.supportsImageMimeType")),
                () -> assertTrue(preparer.contains("record PreparedMedia")),
                () -> assertTrue(preparer.contains("record Rejection")),
                () -> assertTrue(preparer.contains("record Preparation")),
                () -> assertTrue(preparer.contains("enum RejectionReason")),
                () -> assertTrue(preparer.contains("return bytes.clone()")));
    }

    @Test
    void mediaPreparerMustNotAbsorbDiscoveryProjectionEmbeddingOrPersistence() throws IOException {
        String preparer = read(PREPARER);

        assertAll(
                () -> assertFalse(preparer.contains("RagBinaryAssetPort")),
                () -> assertFalse(preparer.contains("binaryAssets")),
                () -> assertFalse(preparer.contains("IRagMultimodalRepository")),
                () -> assertFalse(preparer.contains("multimodalRepository")),
                () -> assertFalse(preparer.contains("RagMultimodalEmbeddingProtocol")),
                () -> assertFalse(preparer.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(preparer.contains("representativeDocument")),
                () -> assertFalse(preparer.contains("mediaContent")),
                () -> assertFalse(preparer.contains("multimodalMetadata")),
                () -> assertFalse(preparer.contains("sha256")),
                () -> assertFalse(preparer.contains("vectorLiteral")),
                () -> assertFalse(preparer.contains("upsert(")),
                () -> assertFalse(preparer.contains("search(")));
    }

    @Test
    void serviceMustDelegateMediaPreparationAndRetainDiscoveryProjectionAndPersistence() throws IOException {
        String service = read(SERVICE);

        assertAll(
                () -> assertTrue(service.contains("RagMultimodalMediaPreparer mediaPreparer")),
                () -> assertTrue(service.contains("mediaPreparer.prepareOriginal(file)")),
                () -> assertTrue(service.contains("mediaPreparer.prepareImage(")),
                () -> assertTrue(service.contains("storePreparedMedia(")),
                () -> assertTrue(service.contains("logMediaRejections(")),
                () -> assertTrue(service.contains("this.mediaPreparer = components.mediaPreparer()")),
                () -> assertFalse(service.contains("new RagMultimodalMediaPreparer(")),
                () -> assertFalse(service.contains("org.apache.pdfbox")),
                () -> assertFalse(service.contains("PDFRenderer")),
                () -> assertFalse(service.contains("ImageIO")),
                () -> assertFalse(service.contains("BufferedImage")),
                () -> assertFalse(service.contains("ByteArrayOutputStream")),
                () -> assertFalse(service.contains("normalizeMimeType(")),
                () -> assertFalse(service.contains("normalizeImageMimeType(")),
                () -> assertFalse(service.contains("private String extension(")),
                () -> assertFalse(service.contains("toPng(")),
                () -> assertTrue(service.contains("RagBinaryAssetPort")),
                () -> assertTrue(service.contains("binaryAssets.isRegularFile(")),
                () -> assertTrue(service.contains("binaryAssets.read(")),
                () -> assertTrue(service.contains("RagMultimodalIngestionProjector ingestionProjector")),
                () -> assertTrue(service.contains("RagMultimodalVectorStore vectorStore")),
                () -> assertTrue(service.contains("RagMultimodalRetrievalCoordinator retrievalCoordinator")),
                () -> assertFalse(service.contains("representativeDocument(")),
                () -> assertFalse(service.contains("mediaContent(")),
                () -> assertFalse(service.contains("multimodalMetadata(")),
                () -> assertFalse(service.contains("sha256(")),
                () -> assertTrue(service.contains("embeddingProtocol.embedImage(")),
                () -> assertTrue(service.contains("vectorStore.upsert(")),
                () -> assertTrue(service.contains("retrievalCoordinator.search(")),
                () -> assertFalse(service.contains("multimodalRepository.upsert(")),
                () -> assertFalse(service.contains("springDocument(")),
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
