package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfTextSegmentationBoundaryArchitectureTest {

    private static final String RAG =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/";

    @Test
    void positionedTextParagraphAndCaptionRulesMustHaveSeparateOwners() throws IOException {
        String facade = read(RAG + "RagPdfChunkExtractor.java");
        String layout = read(RAG + "RagPdfTextLayoutExtractor.java");
        String segmenter = read(RAG + "RagPdfParagraphSegmenter.java");
        String caption = read(RAG + "RagPdfFigureCaptionPolicy.java");

        assertAll(
                () -> assertFalse(facade.contains("PDFTextStripper")),
                () -> assertFalse(facade.contains("TextPosition")),
                () -> assertFalse(facade.contains("paragraphGap")),
                () -> assertFalse(facade.contains("appendLine(")),
                () -> assertFalse(facade.contains("endsWithSentenceBoundary(")),
                () -> assertFalse(facade.contains("FIGURE_CAPTION_PATTERN")),
                () -> assertFalse(facade.contains("captionForImage(")),
                () -> assertTrue(layout.contains("extends PDFTextStripper")),
                () -> assertTrue(layout.contains("List<TextPosition> textPositions")),
                () -> assertTrue(layout.contains("setSortByPosition(true)")),
                () -> assertTrue(layout.contains("collector.getText(pdf)")),
                () -> assertTrue(layout.contains("record TextFragment(")),
                () -> assertTrue(layout.contains("record TextLine(")),
                () -> assertTrue(layout.contains("LineBuilder")),
                () -> assertFalse(layout.contains("RagChunkDraft")),
                () -> assertFalse(layout.contains("RagBinaryAssetPort")),
                () -> assertFalse(layout.contains("PDFStreamEngine")),
                () -> assertFalse(layout.contains("FIGURE_CAPTION_PATTERN")),
                () -> assertTrue(segmenter.contains("record Paragraph(")),
                () -> assertTrue(segmenter.contains("medianLineHeight(")),
                () -> assertTrue(segmenter.contains("paragraphGap")),
                () -> assertTrue(segmenter.contains("appendLine(")),
                () -> assertTrue(segmenter.contains("endsWithSentenceBoundary(")),
                () -> assertTrue(segmenter.contains("current.length() + line.text().length() > maxSegmentChars")),
                () -> assertFalse(segmenter.contains("org.apache.pdfbox")),
                () -> assertFalse(segmenter.contains("RagChunkDraft")),
                () -> assertFalse(segmenter.contains("RagBinaryAssetPort")),
                () -> assertTrue(caption.contains("FIGURE_CAPTION_PATTERN")),
                () -> assertTrue(caption.contains("captionForImage(")),
                () -> assertTrue(caption.contains("imageBottomY + 180D")),
                () -> assertTrue(caption.contains("caption.length() < 600")),
                () -> assertFalse(caption.contains("org.apache.pdfbox")),
                () -> assertFalse(caption.contains("RagChunkDraft")),
                () -> assertFalse(caption.contains("RagBinaryAssetPort")));
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
