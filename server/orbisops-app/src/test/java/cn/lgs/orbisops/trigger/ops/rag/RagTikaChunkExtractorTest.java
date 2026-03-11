package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTikaChunkExtractorTest {

    private final RagChunkMaterializer materializer = new RagChunkMaterializer();
    private final RagVisualFallbackPolicy visualPolicy = new RagVisualFallbackPolicy();

    @Test
    void shouldAdaptResourceJoinNonBlankDocumentsAndPreserveMetadata() {
        AtomicBoolean resourceVerified = new AtomicBoolean();
        RagFileResource file = file("manual.bin", "application/octet-stream", "binary-content");
        RagTikaChunkExtractor extractor = new RagTikaChunkExtractor(materializer, visualPolicy, resource -> {
            try {
                assertEquals("manual.bin", resource.getFilename());
                assertEquals("RAG file resource manual.bin", resource.getDescription());
                assertEquals(file.size(), resource.contentLength());
                assertEquals("binary-content", new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                resourceVerified.set(true);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return List.of(
                    new Document("doc-1", "first evidence", Map.of()),
                    new Document("doc-3", "second evidence", Map.of()));
        });

        List<RagChunkDraft> drafts = extractor.extract(file, metadata(true), "tika-generic", false);

        assertTrue(resourceVerified.get());
        assertEquals(1, drafts.size());
        assertEquals(RagChunkDraft.Boundary.STRUCTURE_BOUNDED, drafts.get(0).boundary());
        assertEquals("first evidence\n\nsecond evidence", drafts.get(0).text());
        assertEquals("tika-generic", drafts.get(0).metadata().get("chunk_strategy"));
        assertEquals("not_applicable", drafts.get(0).metadata().get("structured_rows"));
        assertEquals("not_applicable", drafts.get(0).metadata().get("table_parse"));
        assertFalse(drafts.get(0).metadata().containsKey("visual_parse_recommended"));
    }

    @Test
    void shouldPreserveEmptyTextVisualRecommendationAndTableLikeStrings() {
        RagTikaChunkExtractor extractor = new RagTikaChunkExtractor(materializer, visualPolicy,
                resource -> List.of());

        List<RagChunkDraft> drafts = extractor.extract(
                file("empty.bin", "application/octet-stream", ""),
                metadata(true),
                "tika-fallback",
                true);

        assertEquals(1, drafts.size());
        assertEquals(RagChunkDraft.Boundary.STRUCTURE_BOUNDED, drafts.get(0).boundary());
        assertEquals("Document contains no extractable text with the current parser. OCR or multimodal extraction should be enabled only for high-value documents.", drafts.get(0).text());
        assertEquals("tika-fallback", drafts.get(0).metadata().get("chunk_strategy"));
        assertEquals("false", drafts.get(0).metadata().get("structured_rows"));
        assertEquals("tika_text_fallback", drafts.get(0).metadata().get("table_parse"));
        assertEquals(true, drafts.get(0).metadata().get("visual_parse_recommended"));
        assertEquals("recommended_manual_enable", drafts.get(0).metadata().get("visual_parse_status"));
    }

    @Test
    void shouldPreserveTikaFailureDraftAndNullSafeMetadataMerge() {
        RagTikaChunkExtractor extractor = new RagTikaChunkExtractor(materializer, visualPolicy,
                resource -> {
                    throw new IllegalStateException("boom");
                });

        List<RagChunkDraft> drafts = extractor.extract(
                file("broken.bin", "application/octet-stream", "broken"),
                metadata(false),
                "tika-fallback",
                false);

        assertEquals(1, drafts.size());
        assertEquals(RagChunkDraft.Boundary.EXACT, drafts.get(0).boundary());
        assertEquals("Document parsing failed: boom", drafts.get(0).text());
        assertEquals("tika-fallback", drafts.get(0).metadata().get("chunk_strategy"));
        assertEquals("failed", drafts.get(0).metadata().get("parse_status"));
        assertEquals("boom", drafts.get(0).metadata().get("parse_error"));
        assertFalse(drafts.get(0).metadata().containsKey("structured_rows"));
    }

    @Test
    void shouldUseLowValueVisualStatusForEmptyText() {
        RagTikaChunkExtractor extractor = new RagTikaChunkExtractor(materializer, visualPolicy,
                resource -> List.of());

        RagChunkDraft draft = extractor.extract(
                file("archive.bin", "application/octet-stream", ""),
                metadata(false),
                "tika-generic",
                false).get(0);

        assertEquals("skipped_low_value_or_cost_gate", draft.metadata().get("visual_parse_status"));
    }

    private Map<String, Object> metadata(boolean highValue) {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "manual.bin",
                "max_segment_chars", 1000,
                "hard_split_overlap_chars", 0,
                "high_value_candidate", highValue);
    }

    private RagFileResource file(String name, String contentType, String content) {
        return new ByteArrayRagFileResource(
                "files",
                name,
                contentType,
                content.getBytes(StandardCharsets.UTF_8));
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
