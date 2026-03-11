package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Prepares original, linked-image and PDF-page bytes for multimodal embedding. */
public final class RagMultimodalMediaPreparer {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final RagMultimodalSettings settings;

    public RagMultimodalMediaPreparer(RagMultimodalSettings settings) {
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        this.settings = settings;
    }

    public Preparation prepareOriginal(RagFileResource file) throws Exception {
        if (file == null) {
            return Preparation.empty();
        }
        String fileName = file.fileName();
        String mimeType = normalizeMimeType(file.contentType(), fileName);
        String extension = extension(fileName);
        if (isImage(mimeType, extension)) {
            return prepareOriginalImage(file.readAllBytes(), mimeType);
        }
        if (settings.indexPdfPageImages() && isPdf(mimeType, extension)) {
            return preparePdfPages(file.readAllBytes());
        }
        return Preparation.empty();
    }

    public Preparation prepareImage(
            byte[] imageBytes,
            String mimeType,
            int pageNumber,
            String mediaType) {
        return eligible(imageBytes, normalizeImageMimeType(mimeType), pageNumber, mediaType);
    }

    private Preparation prepareOriginalImage(byte[] imageBytes, String mimeType) throws Exception {
        String normalizedMimeType = normalizeImageMimeType(mimeType);
        if (!settings.supportsImageMimeType(normalizedMimeType)) {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(safeBytes(imageBytes)));
            if (decoded == null) {
                return Preparation.rejected(new Rejection(
                        RejectionReason.UNDECODABLE_IMAGE,
                        normalizedMimeType,
                        1,
                        "image",
                        byteSize(imageBytes)));
            }
            imageBytes = toPng(decoded);
            normalizedMimeType = "image/png";
        }
        return eligible(imageBytes, normalizedMimeType, 1, "image");
    }

    private Preparation preparePdfPages(byte[] pdfBytes) throws Exception {
        List<PreparedMedia> prepared = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(safeBytes(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = Math.min(pdf.getNumberOfPages(), settings.maxPdfPages());
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(
                        page,
                        settings.pdfRenderDpi(),
                        ImageType.RGB);
                Preparation pagePreparation = eligible(
                        toPng(image),
                        "image/png",
                        page + 1,
                        "pdf_page");
                prepared.addAll(pagePreparation.media());
                rejections.addAll(pagePreparation.rejections());
            }
        }
        return new Preparation(prepared, rejections);
    }

    private Preparation eligible(
            byte[] imageBytes,
            String normalizedMimeType,
            int pageNumber,
            String mediaType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Preparation.rejected(new Rejection(
                    RejectionReason.EMPTY_CONTENT,
                    normalizedMimeType,
                    pageNumber,
                    mediaType,
                    0L));
        }
        if (!settings.supportsImageMimeType(normalizedMimeType)) {
            return Preparation.rejected(new Rejection(
                    RejectionReason.UNSUPPORTED_IMAGE_MIME,
                    normalizedMimeType,
                    pageNumber,
                    mediaType,
                    imageBytes.length));
        }
        if (imageBytes.length > settings.maxImageBytes()) {
            return Preparation.rejected(new Rejection(
                    RejectionReason.IMAGE_TOO_LARGE,
                    normalizedMimeType,
                    pageNumber,
                    mediaType,
                    imageBytes.length));
        }
        return Preparation.prepared(new PreparedMedia(
                imageBytes,
                normalizedMimeType,
                pageNumber,
                mediaType));
    }

    private boolean isImage(String mimeType, String extension) {
        return mimeType.startsWith("image/") || IMAGE_EXTENSIONS.contains(extension);
    }

    private boolean isPdf(String mimeType, String extension) {
        return "pdf".equals(extension) || mimeType.contains("pdf");
    }

    private String normalizeMimeType(String contentType, String fileName) {
        if (hasText(contentType)) {
            return normalizeImageMimeType(contentType.toLowerCase(Locale.ROOT));
        }
        return switch (extension(fileName)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private String normalizeImageMimeType(String mimeType) {
        if (!hasText(mimeType)) {
            return "image/png";
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String extension(String fileName) {
        if (!hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private static byte[] safeBytes(byte[] bytes) {
        return bytes == null ? new byte[0] : bytes;
    }

    private static long byteSize(byte[] bytes) {
        return bytes == null ? 0L : bytes.length;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PreparedMedia(byte[] bytes, String mimeType, int pageNumber, String mediaType) {

        public PreparedMedia {
            bytes = safeBytes(bytes).clone();
            mimeType = mimeType == null ? "" : mimeType;
            mediaType = mediaType == null ? "" : mediaType;
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record Rejection(
            RejectionReason reason,
            String mimeType,
            int pageNumber,
            String mediaType,
            long byteSize) {

        public Rejection {
            if (reason == null) throw new IllegalArgumentException("RAG_MULTIMODAL_REJECTION_REASON_REQUIRED");
            mimeType = mimeType == null ? "" : mimeType;
            mediaType = mediaType == null ? "" : mediaType;
        }
    }

    public record Preparation(List<PreparedMedia> media, List<Rejection> rejections) {

        public Preparation {
            media = media == null ? List.of() : List.copyOf(media);
            rejections = rejections == null ? List.of() : List.copyOf(rejections);
        }

        public static Preparation empty() {
            return new Preparation(List.of(), List.of());
        }

        public static Preparation prepared(PreparedMedia media) {
            return new Preparation(List.of(media), List.of());
        }

        public static Preparation rejected(Rejection rejection) {
            return new Preparation(List.of(), List.of(rejection));
        }
    }

    public enum RejectionReason {
        EMPTY_CONTENT,
        UNDECODABLE_IMAGE,
        UNSUPPORTED_IMAGE_MIME,
        IMAGE_TOO_LARGE
    }
}
