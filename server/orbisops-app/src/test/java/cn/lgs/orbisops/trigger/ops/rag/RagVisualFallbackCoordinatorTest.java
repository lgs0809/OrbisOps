package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualFallbackCoordinatorTest {

    private final RagVisualFallbackPolicy policy = new RagVisualFallbackPolicy();
    private final RagVisualFallbackCoordinator coordinator = new RagVisualFallbackCoordinator(
            new RagChunkMaterializer(), policy);

    @Test
    void shouldPreserveHighValueClassificationAndStatus() {
        assertTrue(policy.highValueCandidate("demo-ops", "manual.bin"));
        assertTrue(policy.highValueCandidate("knowledge", "incident-report.pdf"));
        assertTrue(policy.highValueCandidate("知识", "故障排障手册.pdf"));
        assertFalse(policy.highValueCandidate("archive", "reference.pdf"));
        assertEquals("recommended_manual_enable", policy.status(Map.of("high_value_candidate", true)));
        assertEquals("skipped_low_value_or_cost_gate", policy.status(Map.of("high_value_candidate", false)));
    }

    @Test
    void shouldCreateDeterministicPdfFallbackMetadataWhenAnalyzerUnavailable() {
        RagVisualFallbackCoordinator.Result result = coordinator.pdfFallback(
                file("blank.pdf", "application/pdf"),
                metadata(true),
                null);

        assertTrue(result.documents().isEmpty());
        assertEquals(1, result.drafts().size());
        RagChunkDraft draft = result.drafts().get(0);
        assertEquals(RagChunkDraft.Boundary.STRUCTURE_BOUNDED, draft.boundary());
        assertEquals("PDF contains no extractable text or embedded image with the current parser.", draft.text());
        assertEquals(true, draft.metadata().get("visual_parse_recommended"));
        assertEquals("pdfbox_no_extractable_text_or_image", draft.metadata().get("visual_parse_reason"));
        assertEquals("recommended_manual_enable", draft.metadata().get("visual_parse_status"));
    }

    @Test
    void shouldCreateLowValueImagePlaceholderWithExactBoundary() {
        RagVisualFallbackCoordinator.Result result = coordinator.image(
                file("archive.png", "image/png"),
                metadata(false),
                null);

        assertTrue(result.documents().isEmpty());
        assertEquals(1, result.drafts().size());
        RagChunkDraft draft = result.drafts().get(0);
        assertEquals(RagChunkDraft.Boundary.EXACT, draft.boundary());
        assertEquals("image-placeholder", draft.metadata().get("chunk_strategy"));
        assertEquals("text_placeholder_or_multimodal_model", draft.metadata().get("visual_embedding_strategy"));
        assertEquals("text_placeholder", draft.metadata().get("embedding_input_modality"));
        assertEquals(false, draft.metadata().get("visual_parse_recommended"));
        assertEquals("skipped_low_value_or_cost_gate", draft.metadata().get("visual_parse_status"));
        assertTrue(draft.text().startsWith("Image document has no extracted text chunk."));
    }

    @Test
    void shouldPassThroughExternalPdfDocumentsAndProvideFallbackMetadataToAnalyzer() {
        List<Document> external = List.of(new Document("visual-1", "visual evidence", Map.of("visual", true)));
        StubVisualAnalyzer analyzer = new StubVisualAnalyzer(true, external, List.of());

        RagVisualFallbackCoordinator.Result result = coordinator.pdfFallback(
                file("blank.pdf", "application/pdf"),
                metadata(true),
                analyzer);

        assertSame(external, result.documents());
        assertTrue(result.drafts().isEmpty());
        assertEquals(true, analyzer.lastPdfMetadata.get("visual_parse_recommended"));
        assertEquals("pdfbox_no_extractable_text_or_image", analyzer.lastPdfMetadata.get("visual_parse_reason"));
        assertEquals("recommended_manual_enable", analyzer.lastPdfMetadata.get("visual_parse_status"));
    }

    @Test
    void shouldPassThroughExternalImageDocumentsWithoutAddingPlaceholderMetadata() {
        List<Document> external = List.of(new Document("visual-image", "image evidence", Map.of("visual", true)));
        StubVisualAnalyzer analyzer = new StubVisualAnalyzer(true, List.of(), external);
        Map<String, Object> metadata = metadata(true);

        RagVisualFallbackCoordinator.Result result = coordinator.image(
                file("incident.png", "image/png"),
                metadata,
                analyzer);

        assertSame(external, result.documents());
        assertTrue(result.drafts().isEmpty());
        assertSame(metadata, analyzer.lastImageMetadata);
        assertFalse(analyzer.lastImageMetadata.containsKey("chunk_strategy"));
    }

    @Test
    void shouldUseDeterministicFallbackWhenAnalyzerReturnsNoDocuments() {
        StubVisualAnalyzer analyzer = new StubVisualAnalyzer(true, List.of(), List.of());

        RagVisualFallbackCoordinator.Result pdf = coordinator.pdfFallback(
                file("blank.pdf", "application/pdf"), metadata(false), analyzer);
        RagVisualFallbackCoordinator.Result image = coordinator.image(
                file("archive.png", "image/png"), metadata(false), analyzer);

        assertEquals(1, pdf.drafts().size());
        assertEquals(1, image.drafts().size());
        assertEquals("skipped_low_value_or_cost_gate", pdf.drafts().get(0).metadata().get("visual_parse_status"));
        assertEquals("image-placeholder", image.drafts().get(0).metadata().get("chunk_strategy"));
    }

    private Map<String, Object> metadata(boolean highValue) {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "document.bin",
                "max_segment_chars", 1000,
                "hard_split_overlap_chars", 0,
                "high_value_candidate", highValue);
    }

    private RagFileResource file(String name, String contentType) {
        return new ByteArrayRagFileResource("files", name, contentType, new byte[]{1, 2, 3});
    }

    private static final class StubVisualAnalyzer extends RagVisualDocumentAnalyzer {
        private final boolean shouldAnalyze;
        private final List<Document> pdfDocuments;
        private final List<Document> imageDocuments;
        private Map<String, Object> lastPdfMetadata;
        private Map<String, Object> lastImageMetadata;

        private StubVisualAnalyzer(boolean shouldAnalyze,
                                   List<Document> pdfDocuments,
                                   List<Document> imageDocuments) {
            this.shouldAnalyze = shouldAnalyze;
            this.pdfDocuments = pdfDocuments;
            this.imageDocuments = imageDocuments;
        }

        @Override
        public boolean shouldAnalyze(Map<String, Object> metadata) {
            return shouldAnalyze;
        }

        @Override
        public List<Document> analyzePdf(RagFileResource file, Map<String, Object> baseMetadata) {
            lastPdfMetadata = baseMetadata;
            return pdfDocuments;
        }

        @Override
        public List<Document> analyzeImage(RagFileResource file, Map<String, Object> baseMetadata) {
            lastImageMetadata = baseMetadata;
            return imageDocuments;
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
