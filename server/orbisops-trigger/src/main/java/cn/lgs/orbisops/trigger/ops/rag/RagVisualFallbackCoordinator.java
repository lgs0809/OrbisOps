package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * Coordinates optional visual analysis and deterministic PDF/image fallbacks.
 *
 * External visual documents remain Spring AI documents because the analyzer is
 * a Trigger protocol adapter; deterministic fallback evidence remains neutral drafts.
 */
public final class RagVisualFallbackCoordinator {

    private static final String PDF_EMPTY_TEXT = "PDF contains no extractable text or embedded image with the current parser.";
    private static final String IMAGE_PLACEHOLDER_TEXT = "Image document has no extracted text chunk. Enable visual-to-text extraction for answerable text evidence; enable the multimodal model index for image-native retrieval.";

    private final RagChunkMaterializer chunkMaterializer;
    private final RagVisualFallbackPolicy visualFallbackPolicy;

    public RagVisualFallbackCoordinator(RagChunkMaterializer chunkMaterializer,
                                        RagVisualFallbackPolicy visualFallbackPolicy) {
        if (chunkMaterializer == null) throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        if (visualFallbackPolicy == null) throw new IllegalArgumentException("RAG_VISUAL_FALLBACK_POLICY_REQUIRED");
        this.chunkMaterializer = chunkMaterializer;
        this.visualFallbackPolicy = visualFallbackPolicy;
    }

    public Result pdfFallback(RagFileResource file,
                              Map<String, Object> extractionMetadata,
                              RagVisualDocumentAnalyzer analyzer) {
        Map<String, Object> metadata = chunkMaterializer.mergeMetadata(extractionMetadata,
                "visual_parse_recommended", true,
                "visual_parse_reason", "pdfbox_no_extractable_text_or_image");
        metadata.put("visual_parse_status", visualFallbackPolicy.status(metadata));
        if (analyzer != null && analyzer.shouldAnalyze(metadata)) {
            List<Document> documents = analyzer.analyzePdf(file, metadata);
            if (!documents.isEmpty()) {
                return Result.external(documents);
            }
        }
        return Result.drafts(List.of(RagChunkDraft.structureBounded(PDF_EMPTY_TEXT, metadata)));
    }

    public Result image(RagFileResource file,
                        Map<String, Object> baseMetadata,
                        RagVisualDocumentAnalyzer analyzer) {
        if (analyzer != null && analyzer.shouldAnalyze(baseMetadata)) {
            List<Document> documents = analyzer.analyzeImage(file, baseMetadata);
            if (!documents.isEmpty()) {
                return Result.external(documents);
            }
        }
        Map<String, Object> metadata = chunkMaterializer.mergeMetadata(baseMetadata,
                "chunk_strategy", "image-placeholder",
                "visual_embedding_strategy", "text_placeholder_or_multimodal_model",
                "embedding_input_modality", "text_placeholder",
                "visual_parse_recommended", visualFallbackPolicy.highValueCandidate(baseMetadata),
                "visual_parse_status", visualFallbackPolicy.status(baseMetadata));
        return Result.drafts(List.of(RagChunkDraft.exact(IMAGE_PLACEHOLDER_TEXT, metadata)));
    }

    public record Result(List<Document> documents, List<RagChunkDraft> drafts) {
        public Result {
            documents = documents == null ? List.of() : documents;
            drafts = drafts == null ? List.of() : List.copyOf(drafts);
        }

        public static Result external(List<Document> documents) {
            return new Result(documents, List.of());
        }

        public static Result drafts(List<RagChunkDraft> drafts) {
            return new Result(List.of(), drafts);
        }
    }
}
