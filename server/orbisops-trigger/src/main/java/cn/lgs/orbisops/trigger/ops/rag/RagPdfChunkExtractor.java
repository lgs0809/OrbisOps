package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDFBox extraction facade that coordinates positioned text, paragraph,
 * embedded-image evidence and chunk projection boundaries.
 */
public final class RagPdfChunkExtractor {

    private final RagChunkMaterializer chunkMaterializer;
    private final RagPdfTextLayoutExtractor textLayoutExtractor;
    private final RagPdfParagraphSegmenter paragraphSegmenter;
    private final RagPdfEmbeddedImageExtractor imageExtractor;
    private final RagPdfChunkProjector chunkProjector;

    public RagPdfChunkExtractor(
            RagBinaryAssetPort binaryAssets,
            RagChunkMaterializer chunkMaterializer) {
        this(
                chunkMaterializer,
                new RagPdfTextLayoutExtractor(),
                new RagPdfParagraphSegmenter(),
                new RagPdfEmbeddedImageExtractor(
                        binaryAssets,
                        new RagPdfFigureCaptionPolicy()),
                new RagPdfChunkProjector(chunkMaterializer));
    }

    RagPdfChunkExtractor(
            RagChunkMaterializer chunkMaterializer,
            RagPdfTextLayoutExtractor textLayoutExtractor,
            RagPdfParagraphSegmenter paragraphSegmenter,
            RagPdfEmbeddedImageExtractor imageExtractor,
            RagPdfChunkProjector chunkProjector) {
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        if (textLayoutExtractor == null) {
            throw new IllegalArgumentException("PDF_TEXT_LAYOUT_EXTRACTOR_REQUIRED");
        }
        if (paragraphSegmenter == null) {
            throw new IllegalArgumentException("PDF_PARAGRAPH_SEGMENTER_REQUIRED");
        }
        if (imageExtractor == null) {
            throw new IllegalArgumentException("PDF_EMBEDDED_IMAGE_EXTRACTOR_REQUIRED");
        }
        if (chunkProjector == null) {
            throw new IllegalArgumentException("PDF_CHUNK_PROJECTOR_REQUIRED");
        }
        this.chunkMaterializer = chunkMaterializer;
        this.textLayoutExtractor = textLayoutExtractor;
        this.paragraphSegmenter = paragraphSegmenter;
        this.imageExtractor = imageExtractor;
        this.chunkProjector = chunkProjector;
    }

    public Extraction extract(
            RagFileResource file,
            Map<String, Object> baseMetadata,
            int configuredMaxPdfImages) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "读取 PDF 文件失败：" + e.getMessage(),
                    e);
        }

        Map<String, Object> metadata = chunkMaterializer.mergeMetadata(
                baseMetadata,
                "parser_engine", "pdfbox",
                "evidence_boundary", "page_and_paragraph",
                "image_caption_parse", "pdfbox_image_caption");
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            List<RagPdfTextLayoutExtractor.TextLine> lines =
                    textLayoutExtractor.extract(pdf);
            List<RagPdfParagraphSegmenter.Paragraph> paragraphs =
                    paragraphSegmenter.segment(
                            lines,
                            chunkMaterializer.maxSegmentChars(metadata));
            List<RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence> images =
                    imageExtractor.extract(
                            pdf,
                            metadata,
                            lines,
                            configuredMaxPdfImages);

            List<RagChunkDraft> drafts = new ArrayList<>();
            drafts.addAll(chunkProjector.paragraphs(paragraphs, metadata));
            drafts.addAll(chunkProjector.images(images, metadata));
            return new Extraction(metadata, drafts);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PDFBox 结构化解析失败：" + e.getMessage(),
                    e);
        }
    }

    public record Extraction(
            Map<String, Object> metadata,
            List<RagChunkDraft> drafts) {

        public Extraction {
            metadata = metadata == null
                    ? Map.of()
                    : Collections.unmodifiableMap(
                            new LinkedHashMap<>(metadata));
            drafts = drafts == null ? List.of() : List.copyOf(drafts);
        }
    }
}
