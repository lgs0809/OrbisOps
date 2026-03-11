package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagConversationChunkExtractorTest {

    private final RagChunkMaterializer materializer = new RagChunkMaterializer();
    private final RagConversationChunkExtractor extractor = new RagConversationChunkExtractor(materializer);

    @Test
    void shouldPreserveTurnRangesAndGlobalRoleInsertionOrder() {
        String conversation = """
                [2026-05-08 10:00] 用户: 支付接口变慢
                [2026-05-08 10:01] 运维: 先看 Prometheus p95
                [2026-05-08 10:02] 用户: 仍然较慢
                [2026-05-08 10:03] 开发: 同时查 ERROR 日志
                """;

        RagConversationChunkExtractor.Extraction extraction = extractor.extract(conversation, metadata(3000));

        assertTrue(extraction.structured());
        assertEquals(1, extraction.drafts().size());
        RagChunkDraft draft = extraction.drafts().get(0);
        assertEquals(RagChunkDraft.Boundary.EXACT, draft.boundary());
        assertEquals("conversation-turns", draft.metadata().get("chunk_strategy"));
        assertEquals(1, draft.metadata().get("turn_start"));
        assertEquals(4, draft.metadata().get("turn_end"));
        assertEquals("用户,运维,开发", draft.metadata().get("roles"));
        assertTrue(draft.text().contains("Prometheus p95"));
        assertTrue(draft.text().contains("ERROR 日志"));
    }

    @Test
    void shouldKeepPreambleAsFirstTurnWhenFollowedByRoleLines() {
        String conversation = """
                Incident INC-001
                Initial observation.
                用户: 请求失败
                运维: 开始排查
                """;

        RagConversationChunkExtractor.Extraction extraction = extractor.extract(conversation, metadata(3000));

        assertTrue(extraction.structured());
        assertEquals(1, extraction.drafts().size());
        RagChunkDraft draft = extraction.drafts().get(0);
        assertEquals(1, draft.metadata().get("turn_start"));
        assertEquals(3, draft.metadata().get("turn_end"));
        assertEquals("用户,运维", draft.metadata().get("roles"));
        assertTrue(draft.text().startsWith("Incident INC-001\nInitial observation."));
    }

    @Test
    void shouldSplitLongTurnsWithExistingRangeCalculationAndGlobalRoles() {
        String longText = "X".repeat(700);
        String conversation = "用户: " + longText + "\n"
                + "运维: " + longText + "\n"
                + "开发: done\n";

        RagConversationChunkExtractor.Extraction extraction = extractor.extract(conversation, metadata(1000));
        List<RagDocument> documents = materializer.materialize(extraction.drafts());

        assertTrue(extraction.structured());
        assertEquals(2, extraction.drafts().size());
        assertEquals(1, extraction.drafts().get(0).metadata().get("turn_start"));
        assertEquals(1, extraction.drafts().get(0).metadata().get("turn_end"));
        assertEquals(2, extraction.drafts().get(1).metadata().get("turn_start"));
        assertEquals(3, extraction.drafts().get(1).metadata().get("turn_end"));
        assertTrue(extraction.drafts().stream().allMatch(draft ->
                "用户,运维,开发".equals(draft.metadata().get("roles"))));
        assertEquals(List.of(0, 1), documents.stream()
                .map(document -> (Integer) document.metadata().get("chunk_index"))
                .toList());
    }

    @Test
    void shouldSignalParagraphFallbackForSingleTurnOrPlainText() {
        RagConversationChunkExtractor.Extraction oneTurn = extractor.extract(
                "用户: 只有一个问题\ncontinuation",
                metadata(3000));
        RagConversationChunkExtractor.Extraction plain = extractor.extract(
                "plain incident notes",
                metadata(3000));

        assertFalse(oneTurn.structured());
        assertTrue(oneTurn.drafts().isEmpty());
        assertFalse(plain.structured());
        assertTrue(plain.drafts().isEmpty());
    }

    @Test
    void shouldKeepConversationSniffingLimitedToFirstEightyLines() {
        String lateTurns = String.join("\n", Collections.nCopies(80, "plain"))
                + "\n用户: too late\n运维: too late";
        String includedTurns = String.join("\n", Collections.nCopies(78, "plain"))
                + "\n用户: included\n运维: included";

        assertFalse(extractor.looksLikeConversation(lateTurns));
        assertTrue(extractor.looksLikeConversation(includedTurns));
        assertTrue(extractor.looksLikeConversation("USER: first\nAssistant: second"));
    }

    @Test
    void shouldNormalizeBomAndCrLf() {
        RagConversationChunkExtractor.Extraction extraction = extractor.extract(
                "\uFEFF用户: first\r\n运维: second\r\n",
                metadata(3000));

        assertTrue(extraction.structured());
        assertEquals("用户,运维", extraction.drafts().get(0).metadata().get("roles"));
        assertTrue(extraction.drafts().get(0).text().startsWith("用户: first\n"));
    }

    private Map<String, Object> metadata(int maxSegmentChars) {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "incident-chat.txt",
                "max_segment_chars", maxSegmentChars,
                "hard_split_overlap_chars", 0);
    }
}
