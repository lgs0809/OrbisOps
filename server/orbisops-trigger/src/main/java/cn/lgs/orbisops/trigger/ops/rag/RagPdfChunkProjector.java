package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Projects PDF paragraph and embedded-image evidence into chunk drafts. */
public final class RagPdfChunkProjector {

    private final RagChunkMaterializer chunkMaterializer;

    public RagPdfChunkProjector(RagChunkMaterializer chunkMaterializer) {
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        this.chunkMaterializer = chunkMaterializer;
    }

    public List<RagChunkDraft> paragraphs(
            List<RagPdfParagraphSegmenter.Paragraph> paragraphs,
            Map<String, Object> baseMetadata) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return List.of();
        }
        List<RagChunkDraft> drafts = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            RagPdfParagraphSegmenter.Paragraph paragraph = paragraphs.get(i);
            Map<String, Object> metadata = chunkMaterializer.mergeMetadata(
                    baseMetadata,
                    "chunk_strategy", "pdfbox-paragraph",
                    "chunk_type", "text",
                    "paragraph_index", i,
                    "page_start", paragraph.pageStart(),
                    "page_end", paragraph.pageEnd(),
                    "line_start", paragraph.lineStart(),
                    "line_end", paragraph.lineEnd());
            if (paragraph.pageStart() == paragraph.pageEnd()) {
                metadata.put("page_number", paragraph.pageStart());
            }
            drafts.add(RagChunkDraft.structureBounded(
                    paragraph.text(),
                    metadata));
        }
        return List.copyOf(drafts);
    }

    public List<RagChunkDraft> images(
            List<RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence> images,
            Map<String, Object> baseMetadata) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .map(image -> image(image, baseMetadata))
                .toList();
    }

    private RagChunkDraft image(
            RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence image,
            Map<String, Object> baseMetadata) {
        String text = """
                # PDF figure evidence

                Page: %d
                Caption: %s
                Image path: %s

                This chunk represents an embedded PDF image. The caption and page boundary are preserved as retrieval evidence; native image embedding can be added by the multimodal model service when enabled.
                """.formatted(
                image.pageNumber(),
                value(image.caption()),
                image.imagePath());

        Map<String, Object> metadata = chunkMaterializer.mergeMetadata(
                baseMetadata,
                "chunk_strategy", "pdfbox-image-caption",
                "chunk_type", "image_figure",
                "page_number", image.pageNumber(),
                "page_start", image.pageNumber(),
                "page_end", image.pageNumber(),
                "image_path", image.imagePath().toString(),
                "image_file_name", image.imageFileName(),
                "image_mime_type", "image/png",
                "image_sha256", image.sha256(),
                "caption", image.caption(),
                "visual_source", "pdf_embedded_image",
                "embedding_input_modality", "text",
                "multimodal_embedding_strategy", "native_image_plus_caption_when_enabled",
                "bbox_x", image.bboxX(),
                "bbox_y", image.bboxY(),
                "bbox_width", image.bboxWidth(),
                "bbox_height", image.bboxHeight());
        return RagChunkDraft.exact(text, metadata);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
