package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualMediaPreparerTest {

    @Test
    void imagePreparationMustNormalizeMimeAndApplySizeAdmission() throws Exception {
        RagVisualMediaPreparer preparer = new RagVisualMediaPreparer(settings(2, 3, 144));

        RagVisualMediaPreparer.PreparedImage image = preparer.prepareImage(
                resource("image.bin", "", new byte[]{1, 2, 3}));

        assertEquals("image/png", image.mimeType());
        assertEquals("image", image.sourceType());
        assertEquals(1, image.pageNumber());
        assertEquals(0, image.index());
        assertFalse(image.accepted());
        assertEquals("image_too_large", image.rejectionReason());
    }

    @Test
    void pdfPreparationMustRespectPageLimitAndStablePageProjection() throws Exception {
        RagVisualMediaPreparer preparer = new RagVisualMediaPreparer(
                settings(10_000_000L, 2, 72));

        RagVisualMediaPreparer.PdfPreparation result = preparer.preparePdf(
                resource("runbook.pdf", "application/pdf", pdfBytes(3)));

        assertEquals(2, result.pages().size());
        assertEquals(1, result.pages().get(0).pageNumber());
        assertEquals(0, result.pages().get(0).index());
        assertEquals(2, result.pages().get(1).pageNumber());
        assertEquals(1, result.pages().get(1).index());
        assertEquals("pdf_page", result.pages().get(0).sourceType());
        assertEquals("image/png", result.pages().get(0).mimeType());
        assertTrue(result.pages().get(0).bytes().length > 0);
        assertTrue(result.pages().get(0).accepted());
        assertEquals(null, result.failure());
    }

    @Test
    void invalidPdfMustReturnFailureWithoutThrowingAwayPartialContract() {
        RagVisualMediaPreparer preparer = new RagVisualMediaPreparer(
                settings(10_000_000L, 3, 144));

        RagVisualMediaPreparer.PdfPreparation result = preparer.preparePdf(
                resource("broken.pdf", "application/pdf", new byte[]{1, 2, 3}));

        assertTrue(result.pages().isEmpty());
        assertNotNull(result.failure());
    }

    private byte[] pdfBytes(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private RagVisualAnalysisSettings settings(
            long maxImageBytes,
            int maxImages,
            int dpi) {
        return new RagVisualAnalysisSettings(
                true, false, "openai", "https://api.example.com", "key",
                "v1/chat/completions", "model", "low", 30, 1200,
                "max_completion_tokens", "json_schema", 1, maxImages,
                maxImageBytes, dpi);
    }

    private RagFileResource resource(
            String name,
            String contentType,
            byte[] bytes) {
        return new RagFileResource() {
            @Override
            public String name() {
                return "file";
            }

            @Override
            public String originalFilename() {
                return name;
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
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }
}
