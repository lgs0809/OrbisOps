package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfChunkExtractorTest {

    @Test
    void shouldExtractParagraphDraftsWithPdfBoundaryMetadata(@TempDir Path tempDir) throws Exception {
        RagPdfChunkExtractor extractor = extractor(tempDir);
        RagFileResource file = file("incident.pdf", pdfWithText(
                "Checkout latency increased because query_time grew.",
                "Rows_examined indicates a missing composite index."));

        RagPdfChunkExtractor.Extraction extraction = extractor.extract(file, baseMetadata(), 10);

        assertEquals("pdfbox", extraction.metadata().get("parser_engine"));
        assertEquals("page_and_paragraph", extraction.metadata().get("evidence_boundary"));
        assertEquals("pdfbox_image_caption", extraction.metadata().get("image_caption_parse"));
        assertFalse(extraction.drafts().isEmpty());
        RagChunkDraft paragraph = extraction.drafts().stream()
                .filter(draft -> "pdfbox-paragraph".equals(draft.metadata().get("chunk_strategy")))
                .findFirst()
                .orElseThrow();
        assertEquals(RagChunkDraft.Boundary.STRUCTURE_BOUNDED, paragraph.boundary());
        assertTrue(paragraph.text().contains("query_time"));
        assertEquals("text", paragraph.metadata().get("chunk_type"));
        assertEquals(1, paragraph.metadata().get("page_start"));
        assertEquals(1, paragraph.metadata().get("page_end"));
        assertEquals(1, paragraph.metadata().get("page_number"));
        assertEquals(0, paragraph.metadata().get("paragraph_index"));
        assertTrue(((Integer) paragraph.metadata().get("line_start")) >= 1);
        assertTrue(((Integer) paragraph.metadata().get("line_end")) >= 1);
    }

    @Test
    void shouldExtractEmbeddedImageCaptionAndStoreBinary(@TempDir Path tempDir) throws Exception {
        RagPdfChunkExtractor extractor = extractor(tempDir);
        RagFileResource file = file("incident.pdf", pdfWithImageAndCaption());

        RagPdfChunkExtractor.Extraction extraction = extractor.extract(file, baseMetadata(), 10);

        RagChunkDraft image = extraction.drafts().stream()
                .filter(draft -> "pdfbox-image-caption".equals(draft.metadata().get("chunk_strategy")))
                .findFirst()
                .orElseThrow();
        assertEquals(RagChunkDraft.Boundary.EXACT, image.boundary());
        assertEquals("image_figure", image.metadata().get("chunk_type"));
        assertEquals("image/png", image.metadata().get("image_mime_type"));
        assertEquals("pdf_embedded_image", image.metadata().get("visual_source"));
        assertTrue(String.valueOf(image.metadata().get("caption")).contains("Fig. 1"));
        assertTrue(image.text().contains("PDF figure evidence"));
        assertTrue(Files.isRegularFile(Path.of(String.valueOf(image.metadata().get("image_path")))));
        assertTrue(String.valueOf(image.metadata().get("image_sha256")).length() >= 12);
        assertTrue(image.metadata().containsKey("bbox_x"));
        assertTrue(image.metadata().containsKey("bbox_y"));
        assertTrue(image.metadata().containsKey("bbox_width"));
        assertTrue(image.metadata().containsKey("bbox_height"));
    }

    @Test
    void shouldRespectZeroImageLimitWithoutInventingDrafts(@TempDir Path tempDir) throws Exception {
        RagPdfChunkExtractor extractor = extractor(tempDir);
        RagFileResource file = file("image-only.pdf", pdfWithImageOnly());

        RagPdfChunkExtractor.Extraction extraction = extractor.extract(file, baseMetadata(), 0);

        assertTrue(extraction.drafts().isEmpty());
        assertEquals("pdfbox", extraction.metadata().get("parser_engine"));
    }

    @Test
    void shouldReturnEmptyDraftsForBlankPdf(@TempDir Path tempDir) throws Exception {
        RagPdfChunkExtractor extractor = extractor(tempDir);
        byte[] bytes;
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(output);
            bytes = output.toByteArray();
        }

        RagPdfChunkExtractor.Extraction extraction = extractor.extract(file("blank.pdf", bytes), baseMetadata(), 10);

        assertTrue(extraction.drafts().isEmpty());
        assertEquals("page_and_paragraph", extraction.metadata().get("evidence_boundary"));
    }

    @Test
    void shouldPreservePdfBoxFailureContract(@TempDir Path tempDir) {
        RagPdfChunkExtractor extractor = extractor(tempDir);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> extractor.extract(file("broken.pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8)), baseMetadata(), 10));

        assertTrue(error.getMessage().startsWith("PDFBox 结构化解析失败："));
    }

    private RagPdfChunkExtractor extractor(Path root) {
        return new RagPdfChunkExtractor(new TestBinaryAssetPort(root), new RagChunkMaterializer());
    }

    private Map<String, Object> baseMetadata() {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "incident.pdf",
                "max_segment_chars", 1000,
                "hard_split_overlap_chars", 100);
    }

    private RagFileResource file(String name, byte[] bytes) {
        return new ByteArrayRagFileResource("files", name, "application/pdf", bytes);
    }

    private byte[] pdfWithText(String first, String second) throws IOException {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 720);
                content.showText(first);
                content.newLineAtOffset(0, -16);
                content.showText(second);
                content.endText();
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithImageAndCaption() throws IOException {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDImageXObject image = LosslessFactory.createFromImage(pdf, testImage());
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 720);
                content.showText("Checkout latency increased because query_time grew.");
                content.endText();
                content.drawImage(image, 50, 600, 120, 60);
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 580);
                content.showText("Fig. 1: Lock service topology and callback retry queue.");
                content.endText();
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithImageOnly() throws IOException {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(pdf, testImage());
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.drawImage(image, 50, 600, 120, 60);
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private BufferedImage testImage() {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 40, 20);
        graphics.setColor(Color.RED);
        graphics.fillRect(4, 4, 32, 12);
        graphics.dispose();
        return image;
    }

    private static final class TestBinaryAssetPort implements RagBinaryAssetPort {
        private final Path root;

        private TestBinaryAssetPort(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        public Path store(String knowledge, String source, String fileName, byte[] content) throws IOException {
            Path directory = root.resolve(knowledge).resolve(source).normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName).normalize();
            Files.write(target, content);
            return target;
        }

        @Override
        public boolean isRegularFile(Path path) {
            return path != null && Files.isRegularFile(path);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return Files.readAllBytes(path);
        }
    }

    private static final class ByteArrayRagFileResource implements RagFileResource {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private ByteArrayRagFileResource(String name,
                                         String originalFilename,
                                         String contentType,
                                         byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String originalFilename() {
            return originalFilename;
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
        public byte[] readAllBytes() {
            return bytes.clone();
        }

        @Override
        public ByteArrayInputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
