package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Prepares image and PDF page media for visual analysis. */
public final class RagVisualMediaPreparer {

    private final RagVisualAnalysisSettings settings;

    public RagVisualMediaPreparer(RagVisualAnalysisSettings settings) {
        if (settings == null) throw new IllegalArgumentException("RAG_VISUAL_SETTINGS_REQUIRED");
        this.settings = settings;
    }

    public PreparedImage prepareImage(RagFileResource file) throws Exception {
        if (file == null) throw new IllegalArgumentException("RAG_VISUAL_FILE_REQUIRED");
        byte[] bytes = file.getBytes();
        return prepared(
                bytes,
                mimeType(file.getContentType()),
                "image",
                1,
                0);
    }

    public PdfPreparation preparePdf(RagFileResource file) {
        if (file == null) throw new IllegalArgumentException("RAG_VISUAL_FILE_REQUIRED");
        List<PreparedImage> pages = new ArrayList<>();
        Exception failure = null;
        try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = Math.min(pdf.getNumberOfPages(), settings.maxImagesPerDocument());
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(
                        page,
                        settings.pdfRenderDpi(),
                        ImageType.RGB);
                pages.add(prepared(
                        toPng(image),
                        "image/png",
                        "pdf_page",
                        page + 1,
                        page));
            }
        } catch (Exception e) {
            failure = e;
        }
        return new PdfPreparation(pages, failure);
    }

    private PreparedImage prepared(
            byte[] bytes,
            String mimeType,
            String sourceType,
            int pageNumber,
            int index) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        boolean accepted = safeBytes.length <= settings.maxImageBytes();
        return new PreparedImage(
                safeBytes,
                mimeType,
                sourceType,
                pageNumber,
                index,
                accepted,
                accepted ? "" : "image_too_large");
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private String mimeType(String contentType) {
        return contentType == null || contentType.isBlank() ? "image/png" : contentType;
    }

    public record PreparedImage(
            byte[] bytes,
            String mimeType,
            String sourceType,
            int pageNumber,
            int index,
            boolean accepted,
            String rejectionReason) {
    }

    public record PdfPreparation(
            List<PreparedImage> pages,
            Exception failure) {

        public PdfPreparation {
            pages = pages == null ? List.of() : List.copyOf(pages);
        }
    }
}
