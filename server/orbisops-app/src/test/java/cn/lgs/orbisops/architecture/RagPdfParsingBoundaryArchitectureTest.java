package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfParsingBoundaryArchitectureTest {

    private static final String RAG = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/";
    private static final String PARSER = RAG + "RagDocumentParser.java";
    private static final String COORDINATOR = RAG + "RagDocumentParseCoordinator.java";
    private static final String PDF_EXTRACTOR = RAG + "RagPdfChunkExtractor.java";
    private static final String MATERIALIZER = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/service/RagChunkMaterializer.java";

    @Test
    void pdfBoxFacadeMustCoordinateDedicatedPdfBoundaries() throws IOException {
        String extractor = read(PDF_EXTRACTOR);

        assertAll(
                () -> assertTrue(extractor.contains("class RagPdfChunkExtractor")),
                () -> assertTrue(extractor.contains("Loader.loadPDF(bytes)")),
                () -> assertTrue(extractor.contains("RagPdfTextLayoutExtractor textLayoutExtractor")),
                () -> assertTrue(extractor.contains("RagPdfParagraphSegmenter paragraphSegmenter")),
                () -> assertTrue(extractor.contains("RagPdfEmbeddedImageExtractor imageExtractor")),
                () -> assertTrue(extractor.contains("RagPdfChunkProjector chunkProjector")),
                () -> assertTrue(extractor.contains("textLayoutExtractor.extract(pdf)")),
                () -> assertTrue(extractor.contains("paragraphSegmenter.segment(")),
                () -> assertTrue(extractor.contains("imageExtractor.extract(")),
                () -> assertTrue(extractor.contains("chunkProjector.paragraphs(")),
                () -> assertTrue(extractor.contains("chunkProjector.images(")),
                () -> assertTrue(extractor.contains("record Extraction")),
                () -> assertFalse(extractor.contains("PDFTextStripper")),
                () -> assertFalse(extractor.contains("PDFStreamEngine")),
                () -> assertFalse(extractor.contains("binaryAssets.store")),
                () -> assertFalse(extractor.contains("pdfbox-paragraph")),
                () -> assertFalse(extractor.contains("pdfbox-image-caption")),
                () -> assertFalse(extractor.contains("FIGURE_CAPTION_PATTERN")),
                () -> assertFalse(extractor.contains("ImageIO")),
                () -> assertFalse(extractor.contains("MessageDigest")),
                () -> assertFalse(extractor.contains("RagChunkDraft.structureBounded")),
                () -> assertFalse(extractor.contains("RagChunkDraft.exact")),
                () -> assertFalse(extractor.contains("org.springframework.ai")),
                () -> assertFalse(extractor.contains("TikaDocumentReader")),
                () -> assertFalse(extractor.contains("org.apache.poi")),
                () -> assertFalse(extractor.contains("RagVisualDocumentAnalyzer")),
                () -> assertTrue(extractor.lines().count() <= 140));
    }

    @Test
    void coordinatorMustOnlyCoordinatePdfExtractionMaterializationAndVisualFallback() throws IOException {
        String parser = read(PARSER);
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertFalse(parser.contains("RagPdfChunkExtractor")),
                () -> assertTrue(coordinator.contains("RagPdfChunkExtractor")),
                () -> assertTrue(coordinator.contains("pdfChunkExtractor.extract(")),
                () -> assertTrue(coordinator.contains("settings.maxPdfImages()")),
                () -> assertTrue(coordinator.contains("projector.materialize(extraction.drafts())")),
                () -> assertTrue(coordinator.contains("visualFallbackCoordinator.pdfFallback(")),
                () -> assertFalse(coordinator.contains("org.apache.pdfbox")),
                () -> assertFalse(coordinator.contains("PDFTextStripper")),
                () -> assertFalse(coordinator.contains("PDFStreamEngine")),
                () -> assertFalse(coordinator.contains("PDImageXObject")),
                () -> assertFalse(coordinator.contains("FIGURE_CAPTION_PATTERN")),
                () -> assertFalse(coordinator.contains("TextFragment")),
                () -> assertFalse(coordinator.contains("captionForImage")),
                () -> assertFalse(coordinator.contains("private String sha256")),
                () -> assertTrue(parser.lines().count() <= 120),
                () -> assertTrue(coordinator.lines().count() <= 230));
    }

    @Test
    void domainChunkMaterializationMustNotAbsorbPdfBoxTypes() throws IOException {
        String materializer = read(MATERIALIZER);

        assertAll(
                () -> assertFalse(materializer.contains("org.apache.pdfbox")),
                () -> assertFalse(materializer.contains("PDDocument")),
                () -> assertFalse(materializer.contains("PDImageXObject")),
                () -> assertFalse(materializer.contains("PDFTextStripper")));
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
