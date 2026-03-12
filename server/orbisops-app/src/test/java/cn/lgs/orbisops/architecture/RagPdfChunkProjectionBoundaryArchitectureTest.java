package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfChunkProjectionBoundaryArchitectureTest {

    private static final String RAG =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/";

    @Test
    void paragraphImageMetadataAndDraftProjectionMustStayOutsideFacade() throws IOException {
        String facade = read(RAG + "RagPdfChunkExtractor.java");
        String projector = read(RAG + "RagPdfChunkProjector.java");

        assertAll(
                () -> assertTrue(facade.contains("chunkProjector.paragraphs(")),
                () -> assertTrue(facade.contains("chunkProjector.images(")),
                () -> assertFalse(facade.contains("RagChunkDraft.structureBounded(")),
                () -> assertFalse(facade.contains("RagChunkDraft.exact(")),
                () -> assertFalse(facade.contains("pdfbox-paragraph")),
                () -> assertFalse(facade.contains("pdfbox-image-caption")),
                () -> assertFalse(facade.contains("paragraph_index")),
                () -> assertFalse(facade.contains("image_path")),
                () -> assertFalse(facade.contains("bbox_x")),
                () -> assertFalse(facade.contains("# PDF figure evidence")),
                () -> assertTrue(projector.contains("RagChunkDraft.structureBounded(")),
                () -> assertTrue(projector.contains("RagChunkDraft.exact(")),
                () -> assertTrue(projector.contains("pdfbox-paragraph")),
                () -> assertTrue(projector.contains("pdfbox-image-caption")),
                () -> assertTrue(projector.contains("paragraph_index")),
                () -> assertTrue(projector.contains("page_start")),
                () -> assertTrue(projector.contains("line_start")),
                () -> assertTrue(projector.contains("image_path")),
                () -> assertTrue(projector.contains("image_sha256")),
                () -> assertTrue(projector.contains("caption")),
                () -> assertTrue(projector.contains("bbox_x")),
                () -> assertTrue(projector.contains("# PDF figure evidence")),
                () -> assertTrue(projector.contains("native_image_plus_caption_when_enabled")),
                () -> assertFalse(projector.contains("org.apache.pdfbox")),
                () -> assertFalse(projector.contains("RagBinaryAssetPort")),
                () -> assertFalse(projector.contains("PDFStreamEngine")),
                () -> assertFalse(projector.contains("Loader.loadPDF")),
                () -> assertFalse(projector.contains("ImageIO")),
                () -> assertFalse(projector.contains("MessageDigest")),
                () -> assertFalse(projector.contains("@Service")));
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
