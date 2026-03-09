package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfEmbeddedImageBoundaryArchitectureTest {

    private static final String RAG =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/";

    @Test
    void imageStreamStorageHashAndBoundsMustHaveOneEvidenceOwner() throws IOException {
        String facade = read(RAG + "RagPdfChunkExtractor.java");
        String images = read(RAG + "RagPdfEmbeddedImageExtractor.java");
        String projector = read(RAG + "RagPdfChunkProjector.java");

        assertAll(
                () -> assertFalse(facade.contains("PDFStreamEngine")),
                () -> assertFalse(facade.contains("PDImageXObject")),
                () -> assertFalse(facade.contains("ImageIO")),
                () -> assertFalse(facade.contains("MessageDigest")),
                () -> assertFalse(facade.contains("binaryAssets.store")),
                () -> assertFalse(facade.contains("sanitizePathPart(")),
                () -> assertFalse(facade.contains("bbox_x")),
                () -> assertTrue(images.contains("extends PDFStreamEngine")),
                () -> assertTrue(images.contains("new DrawObject(this)")),
                () -> assertTrue(images.contains("PDImageXObject image")),
                () -> assertTrue(images.contains("ImageIO.write(")),
                () -> assertTrue(images.contains("MessageDigest.getInstance(\"SHA-256\")")),
                () -> assertTrue(images.contains("binaryAssets.store(")),
                () -> assertTrue(images.contains("sanitizePathPart(")),
                () -> assertTrue(images.contains("captionPolicy.captionForImage(")),
                () -> assertTrue(images.contains("getCurrentTransformationMatrix()")),
                () -> assertTrue(images.contains("record EmbeddedImageEvidence(")),
                () -> assertTrue(images.contains("Math.max(0, Math.min(200, configuredMaxPdfImages))")),
                () -> assertFalse(images.contains("RagChunkDraft")),
                () -> assertFalse(images.contains("RagChunkMaterializer")),
                () -> assertFalse(images.contains("pdfbox-image-caption")),
                () -> assertFalse(images.contains("# PDF figure evidence")),
                () -> assertTrue(projector.contains("RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence")),
                () -> assertFalse(projector.contains("PDFStreamEngine")),
                () -> assertFalse(projector.contains("PDImageXObject")),
                () -> assertFalse(projector.contains("ImageIO")),
                () -> assertFalse(projector.contains("MessageDigest")),
                () -> assertFalse(projector.contains("binaryAssets.store")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
