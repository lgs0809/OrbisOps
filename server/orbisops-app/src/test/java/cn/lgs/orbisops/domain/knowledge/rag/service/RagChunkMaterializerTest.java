package cn.lgs.orbisops.domain.knowledge.rag.service;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagChunkMaterializerTest {

    private final RagChunkMaterializer materializer = new RagChunkMaterializer();

    @Test
    void shouldMergeMetadataWithoutMutatingSourceAndPreferOverlayValues() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("knowledge", "orders");
        source.put("chunk_strategy", "source");

        Map<String, Object> merged = materializer.mergeMetadata(source,
                "chunk_strategy", "paragraph",
                "page_number", 2,
                "ignored", null,
                null, "ignored");

        assertEquals("source", source.get("chunk_strategy"));
        assertFalse(source.containsKey("page_number"));
        assertEquals("paragraph", merged.get("chunk_strategy"));
        assertEquals(2, merged.get("page_number"));
        assertFalse(merged.containsKey("ignored"));
    }

    @Test
    void shouldKeepStableIdentifierSeedAndRemainDeterministic() {
        Map<String, Object> metadata = metadata(1000, 0);
        String text = "stable evidence";
        String seed = "PROJECT:demo-project:lock-recovery:Operations:runbook.txt:0:" + text.hashCode();
        String expectedId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();

        List<RagDocument> first = materializer.materialize(List.of(RagChunkDraft.exact(text, metadata)));
        List<RagDocument> second = materializer.materialize(List.of(RagChunkDraft.exact(text, metadata)));

        assertEquals(expectedId, first.get(0).id());
        assertEquals(first, second);
        assertNotEquals(first.get(0).id(), materializer.materialize(List.of(RagChunkDraft.exact(
                text,
                materializer.mergeMetadata(metadata, "project_id", "another-project")))).get(0).id());
    }

    @Test
    void shouldSplitParagraphsBeforeUsingHardSplit() {
        String firstParagraph = "A".repeat(600);
        String secondParagraph = "B".repeat(500);

        List<RagDocument> documents = materializer.materialize(List.of(RagChunkDraft.structureBounded(
                "\uFEFF" + firstParagraph + "\r\n\r\n" + secondParagraph,
                metadata(1000, 100))));

        assertEquals(2, documents.size());
        assertEquals(firstParagraph, documents.get(0).text());
        assertEquals(secondParagraph, documents.get(1).text());
        assertEquals(1, documents.get(0).metadata().get("chunk_part"));
        assertEquals(2, documents.get(1).metadata().get("chunk_part"));
        assertFalse(Boolean.TRUE.equals(documents.get(0).metadata().get("secondary_split")));
        assertFalse(Boolean.TRUE.equals(documents.get(1).metadata().get("secondary_split")));
    }

    @Test
    void shouldHardSplitOversizedStructuralBlockWithConfiguredOverlap() {
        String oversized = "X".repeat(2200);

        List<RagDocument> documents = materializer.materialize(List.of(RagChunkDraft.structureBounded(
                oversized,
                metadata(1000, 100))));

        assertEquals(3, documents.size());
        assertEquals(1000, documents.get(0).text().length());
        assertEquals(1000, documents.get(1).text().length());
        assertEquals(400, documents.get(2).text().length());
        assertEquals(documents.get(0).text().substring(900), documents.get(1).text().substring(0, 100));
        assertEquals(documents.get(1).text().substring(900), documents.get(2).text().substring(0, 100));
        assertTrue(documents.stream().allMatch(document -> Boolean.TRUE.equals(document.metadata().get("secondary_split"))));
        assertTrue(documents.stream().allMatch(document -> "oversized_structural_block".equals(document.metadata().get("split_reason"))));
        assertTrue(documents.stream().allMatch(document -> Integer.valueOf(100).equals(document.metadata().get("hard_split_overlap_chars"))));
    }

    @Test
    void shouldClampMaxLengthAndOverlapAtExistingBoundaries() {
        List<RagDocument> documents = materializer.materialize(List.of(RagChunkDraft.structureBounded(
                "X".repeat(1600),
                metadata(100, 900))));

        assertEquals(3, documents.size());
        assertEquals(1000, documents.get(0).text().length());
        assertEquals(500, documents.get(0).metadata().get("hard_split_overlap_chars"));
        assertEquals(1000, materializer.maxSegmentChars(Map.of("max_segment_chars", 100)));
        assertEquals(12000, materializer.maxSegmentChars(Map.of("max_segment_chars", 20000)));
    }

    @Test
    void shouldIgnoreEmptyBoundedTextAndKeepBoundaryLengthAsSingleChunk() {
        List<RagDocument> documents = materializer.materialize(List.of(
                RagChunkDraft.structureBounded(" \r\n\t ", metadata(1000, 0)),
                RagChunkDraft.structureBounded("Y".repeat(1000), metadata(1000, 0))));

        assertEquals(1, documents.size());
        assertEquals(1000, documents.get(0).text().length());
        assertEquals(0, documents.get(0).metadata().get("chunk_index"));
    }

    @Test
    void shouldIgnoreEmptyExactDraftsAndKeepIndexesContinuous() {
        List<RagDocument> documents = materializer.materialize(List.of(
                RagChunkDraft.exact("   \n\t ", metadata(1000, 0)),
                RagChunkDraft.exact("usable evidence", metadata(1000, 0))));

        assertEquals(1, documents.size());
        assertEquals("usable evidence", documents.get(0).text());
        assertEquals(0, documents.get(0).metadata().get("chunk_index"));
    }

    @Test
    void shouldPreserveDraftOrderAndAssignContinuousIndexesAcrossSplitResults() {
        List<RagDocument> documents = materializer.materialize(List.of(
                RagChunkDraft.exact("first", metadata(1000, 0)),
                RagChunkDraft.structureBounded("X".repeat(1100), metadata(1000, 0)),
                RagChunkDraft.exact("last", metadata(1000, 0))));

        assertEquals(4, documents.size());
        assertEquals("first", documents.get(0).text());
        assertEquals("X".repeat(1000), documents.get(1).text());
        assertEquals("X".repeat(100), documents.get(2).text());
        assertEquals("last", documents.get(3).text());
        assertEquals(List.of(0, 1, 2, 3), documents.stream()
                .map(document -> (Integer) document.metadata().get("chunk_index"))
                .toList());
    }

    @Test
    void shouldOverrideIncomingChunkIndexButKeepFormatMetadata() {
        Map<String, Object> metadata = materializer.mergeMetadata(metadata(1000, 0),
                "chunk_index", 99,
                "heading_path", "ROOT > Recovery",
                "page_number", 7);

        RagDocument document = materializer.materialize(List.of(RagChunkDraft.exact("evidence", metadata))).get(0);

        assertEquals(0, document.metadata().get("chunk_index"));
        assertEquals("ROOT > Recovery", document.metadata().get("heading_path"));
        assertEquals(7, document.metadata().get("page_number"));
    }

    private Map<String, Object> metadata(int maxSegmentChars, int overlapChars) {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "lock-recovery",
                "rag_name", "Operations",
                "source", "runbook.txt",
                "max_segment_chars", maxSegmentChars,
                "hard_split_overlap_chars", overlapChars);
    }
}
