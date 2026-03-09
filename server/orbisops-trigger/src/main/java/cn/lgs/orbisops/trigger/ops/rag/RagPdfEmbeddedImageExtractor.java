package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Extracts, stores and locates embedded PDF image evidence. */
public final class RagPdfEmbeddedImageExtractor {

    private final RagBinaryAssetPort binaryAssets;
    private final RagPdfFigureCaptionPolicy captionPolicy;

    public RagPdfEmbeddedImageExtractor(
            RagBinaryAssetPort binaryAssets,
            RagPdfFigureCaptionPolicy captionPolicy) {
        if (binaryAssets == null) {
            throw new IllegalArgumentException("RAG_BINARY_ASSET_PORT_REQUIRED");
        }
        if (captionPolicy == null) {
            throw new IllegalArgumentException("PDF_FIGURE_CAPTION_POLICY_REQUIRED");
        }
        this.binaryAssets = binaryAssets;
        this.captionPolicy = captionPolicy;
    }

    public List<EmbeddedImageEvidence> extract(
            PDDocument pdf,
            Map<String, Object> baseMetadata,
            List<RagPdfTextLayoutExtractor.TextLine> lines,
            int configuredMaxPdfImages) throws IOException {
        if (pdf == null) throw new IllegalArgumentException("PDF_DOCUMENT_REQUIRED");
        int imageLimit = Math.max(0, Math.min(200, configuredMaxPdfImages));
        ImageStreamEngine extractor = new ImageStreamEngine(
                baseMetadata == null ? Map.of() : baseMetadata,
                lines,
                imageLimit);
        for (int pageIndex = 0;
             pageIndex < pdf.getNumberOfPages()
                     && extractor.imageCount() < imageLimit;
             pageIndex++) {
            PDPage page = pdf.getPage(pageIndex);
            extractor.process(
                    page,
                    pageIndex + 1,
                    page.getMediaBox().getHeight());
        }
        return extractor.evidence();
    }

    private byte[] toPngBytes(PDImageXObject image) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image.getImage(), "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            return String.valueOf(Arrays.hashCode(bytes));
        }
    }

    private String stripExtension(String fileName) {
        if (!hasText(fileName)) {
            return "document";
        }
        int index = fileName.lastIndexOf('.');
        return index < 0 ? fileName : fileName.substring(0, index);
    }

    private String sanitizePathPart(String value) {
        String sanitized = (value == null ? "" : value)
                .replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]+", "_");
        return hasText(sanitized) ? sanitized : "default";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private final class ImageStreamEngine extends PDFStreamEngine {

        private final List<EmbeddedImageEvidence> evidence = new ArrayList<>();
        private final Map<String, Object> baseMetadata;
        private final List<RagPdfTextLayoutExtractor.TextLine> lines;
        private final int imageLimit;
        private int currentPage;
        private float currentPageHeight;

        private ImageStreamEngine(
                Map<String, Object> baseMetadata,
                List<RagPdfTextLayoutExtractor.TextLine> lines,
                int imageLimit) {
            this.baseMetadata = baseMetadata;
            this.lines = lines == null ? List.of() : lines;
            this.imageLimit = imageLimit;
            addOperator(new Concatenate(this));
            addOperator(new DrawObject(this));
            addOperator(new SetGraphicsStateParameters(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new SetMatrix(this));
        }

        private void process(
                PDPage page,
                int pageNumber,
                float pageHeight) throws IOException {
            if (evidence.size() >= imageLimit) {
                return;
            }
            currentPage = pageNumber;
            currentPageHeight = pageHeight;
            processPage(page);
        }

        @Override
        protected void processOperator(
                Operator operator,
                List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName())
                    && !operands.isEmpty()
                    && operands.get(0) instanceof COSName objectName) {
                PDXObject xObject = getResources().getXObject(objectName);
                if (xObject instanceof PDImageXObject image
                        && evidence.size() < imageLimit) {
                    captureImage(image);
                    return;
                }
            }
            super.processOperator(operator, operands);
        }

        private void captureImage(PDImageXObject image) throws IOException {
            byte[] pngBytes = toPngBytes(image);
            String sha = sha256(pngBytes);
            String imageFileName = "page-"
                    + String.format(Locale.ROOT, "%03d", currentPage)
                    + "-image-"
                    + String.format(Locale.ROOT, "%02d", evidence.size() + 1)
                    + "-"
                    + sha.substring(0, Math.min(12, sha.length()))
                    + ".png";
            String knowledge = sanitizePathPart(String.valueOf(
                    baseMetadata.getOrDefault("knowledge", "default")));
            String source = sanitizePathPart(stripExtension(String.valueOf(
                    baseMetadata.getOrDefault("source", "document"))));
            Path imagePath = binaryAssets.store(
                    knowledge,
                    source,
                    imageFileName,
                    pngBytes);

            Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
            double x = matrix.getTranslateX();
            double y = matrix.getTranslateY();
            double width = Math.abs(matrix.getScalingFactorX());
            double height = Math.abs(matrix.getScalingFactorY());
            double topY = Math.max(0D, currentPageHeight - y - height);
            double bottomY = Math.max(topY, currentPageHeight - y);
            String caption = captionPolicy.captionForImage(
                    lines,
                    currentPage,
                    bottomY);

            evidence.add(new EmbeddedImageEvidence(
                    currentPage,
                    imagePath,
                    imageFileName,
                    sha,
                    caption,
                    round(x),
                    round(topY),
                    round(width),
                    round(height)));
        }

        private double round(double value) {
            return Math.round(value * 100D) / 100D;
        }

        private int imageCount() {
            return evidence.size();
        }

        private List<EmbeddedImageEvidence> evidence() {
            return List.copyOf(evidence);
        }
    }

    public record EmbeddedImageEvidence(
            int pageNumber,
            Path imagePath,
            String imageFileName,
            String sha256,
            String caption,
            double bboxX,
            double bboxY,
            double bboxWidth,
            double bboxHeight) {
    }
}
