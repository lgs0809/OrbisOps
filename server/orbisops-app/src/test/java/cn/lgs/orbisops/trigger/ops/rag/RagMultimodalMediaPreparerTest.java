package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalMediaPreparerTest {

    @Test
    void supportedOriginalImageMustPreserveBytesAndInferMimeFromExtension() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3};
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(true, 3, 144, 1024));

        RagMultimodalMediaPreparer.Preparation preparation = preparer.prepareOriginal(
                resource("sample.png", "", imageBytes));

        assertEquals(1, preparation.media().size());
        assertTrue(preparation.rejections().isEmpty());
        RagMultimodalMediaPreparer.PreparedMedia media = preparation.media().get(0);
        assertArrayEquals(imageBytes, media.bytes());
        assertEquals("image/png", media.mimeType());
        assertEquals(1, media.pageNumber());
        assertEquals("image", media.mediaType());
    }

    @Test
    void unsupportedOriginalImageMimeMustDecodeAndConvertToPng() throws Exception {
        byte[] sourcePng = pngBytes(4, 3);
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(true, 3, 144, 4096));

        RagMultimodalMediaPreparer.Preparation preparation = preparer.prepareOriginal(
                resource("sample.bmp", "image/bmp", sourcePng));

        assertEquals(1, preparation.media().size());
        assertTrue(preparation.rejections().isEmpty());
        RagMultimodalMediaPreparer.PreparedMedia media = preparation.media().get(0);
        assertEquals("image/png", media.mimeType());
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(media.bytes()));
        assertNotNull(decoded);
        assertEquals(4, decoded.getWidth());
        assertEquals(3, decoded.getHeight());
    }

    @Test
    void undecodableOriginalImageMustReturnTypedRejection() throws Exception {
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(true, 3, 144, 4096));

        RagMultimodalMediaPreparer.Preparation preparation = preparer.prepareOriginal(
                resource("sample.bmp", "image/bmp", new byte[]{9, 8, 7}));

        assertTrue(preparation.media().isEmpty());
        assertEquals(1, preparation.rejections().size());
        RagMultimodalMediaPreparer.Rejection rejection = preparation.rejections().get(0);
        assertEquals(RagMultimodalMediaPreparer.RejectionReason.UNDECODABLE_IMAGE, rejection.reason());
        assertEquals("image/bmp", rejection.mimeType());
        assertEquals(3L, rejection.byteSize());
    }

    @Test
    void linkedImageMustNormalizeJpgMimeWithoutDecoding() {
        byte[] bytes = new byte[]{5, 6};
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(true, 3, 144, 4096));

        RagMultimodalMediaPreparer.Preparation preparation = preparer.prepareImage(
                bytes, "IMAGE/JPG; charset=binary", 7, "linked_image");

        assertEquals(1, preparation.media().size());
        RagMultimodalMediaPreparer.PreparedMedia media = preparation.media().get(0);
        assertEquals("image/jpeg", media.mimeType());
        assertEquals(7, media.pageNumber());
        assertEquals("linked_image", media.mediaType());
        assertArrayEquals(bytes, media.bytes());
    }

    @Test
    void linkedUnsupportedMimeAndOversizeMustReturnTypedRejections() {
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(true, 3, 144, 2));

        RagMultimodalMediaPreparer.Preparation unsupported = preparer.prepareImage(
                new byte[]{1}, "image/bmp", 1, "linked_image");
        RagMultimodalMediaPreparer.Preparation oversized = preparer.prepareImage(
                new byte[]{1, 2, 3}, "image/png", 2, "linked_image");

        assertEquals(
                RagMultimodalMediaPreparer.RejectionReason.UNSUPPORTED_IMAGE_MIME,
                unsupported.rejections().get(0).reason());
        assertEquals(
                RagMultimodalMediaPreparer.RejectionReason.IMAGE_TOO_LARGE,
                oversized.rejections().get(0).reason());
        assertEquals(3L, oversized.rejections().get(0).byteSize());
    }

    @Test
    void pdfPreparationMustApplyPageLimitAndRenderDpi() throws Exception {
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(true, 1, 144, 100_000));

        RagMultimodalMediaPreparer.Preparation preparation = preparer.prepareOriginal(
                resource("sample.pdf", "application/pdf", pdfBytes(2)));

        assertEquals(1, preparation.media().size());
        assertTrue(preparation.rejections().isEmpty());
        RagMultimodalMediaPreparer.PreparedMedia media = preparation.media().get(0);
        assertEquals("image/png", media.mimeType());
        assertEquals("pdf_page", media.mediaType());
        assertEquals(1, media.pageNumber());
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(media.bytes()));
        assertNotNull(rendered);
        assertEquals(144, rendered.getWidth());
        assertEquals(144, rendered.getHeight());
    }

    @Test
    void disabledPdfPageIndexingMustNotReadOrRenderPdf() throws Exception {
        RagMultimodalMediaPreparer preparer = new RagMultimodalMediaPreparer(settings(false, 3, 144, 4096));
        CountingResource file = new CountingResource("sample.pdf", "application/pdf", new byte[]{1, 2, 3});

        RagMultimodalMediaPreparer.Preparation preparation = preparer.prepareOriginal(file);

        assertTrue(preparation.media().isEmpty());
        assertTrue(preparation.rejections().isEmpty());
        assertEquals(0, file.openCalls);
    }

    @Test
    void preparedMediaMustDefensivelyCopyBytes() {
        byte[] source = new byte[]{1, 2};
        RagMultimodalMediaPreparer.PreparedMedia media = new RagMultimodalMediaPreparer.PreparedMedia(
                source, "image/png", 1, "image");

        source[0] = 9;
        byte[] firstRead = media.bytes();
        firstRead[1] = 8;

        assertArrayEquals(new byte[]{1, 2}, media.bytes());
    }

    private RagMultimodalSettings settings(
            boolean indexPdfPages,
            int maxPdfPages,
            int dpi,
            long maxImageBytes) {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                2048,
                true,
                false,
                true,
                indexPdfPages,
                maxPdfPages,
                dpi,
                maxImageBytes,
                3000,
                8,
                30,
                1);
    }

    private RagFileResource resource(String fileName, String contentType, byte[] bytes) {
        return new CountingResource(fileName, contentType, bytes);
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private byte[] pdfBytes(int pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < pages; page++) {
                document.addPage(new PDPage(new PDRectangle(72, 72)));
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static final class CountingResource implements RagFileResource {

        private final String fileName;
        private final String contentType;
        private final byte[] bytes;
        private int openCalls;

        private CountingResource(String fileName, String contentType, byte[] bytes) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String name() {
            return fileName;
        }

        @Override
        public String originalFilename() {
            return fileName;
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
            openCalls++;
            return new ByteArrayInputStream(bytes);
        }
    }
}
