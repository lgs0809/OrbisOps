package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCodeChunkExtractorTest {

    private final RagChunkMaterializer materializer = new RagChunkMaterializer();
    private final RagCodeChunkExtractor extractor = new RagCodeChunkExtractor(materializer);

    @Test
    void shouldPreservePackageSymbolAndLineMetadata() {
        String code = """
                package cn.example.ops;

                public class LockService {
                    public void lockOrder(String traceId) {
                        System.out.println(traceId);
                    }
                }
                """;

        List<RagChunkDraft> drafts = extractor.extract(code, metadata(3000));

        assertEquals(1, drafts.size());
        RagChunkDraft draft = drafts.get(0);
        assertEquals(RagChunkDraft.Boundary.EXACT, draft.boundary());
        assertEquals("code-symbol", draft.metadata().get("chunk_strategy"));
        assertEquals("cn.example.ops", draft.metadata().get("package"));
        assertEquals("lockOrder", draft.metadata().get("symbol"));
        assertEquals(1, draft.metadata().get("line_start"));
        assertEquals(8, draft.metadata().get("line_end"));
        assertTrue(draft.text().contains("public class LockService"));
        assertTrue(draft.text().contains("lockOrder"));
    }

    @Test
    void shouldFlushPreambleBeforeBoundaryUsingExistingLineCalculation() {
        String preamble = "// " + "P".repeat(360) + "\n";
        String code = preamble + "public class RecoveryService {\n"
                + "    public void recover() {}\n"
                + "}\n";

        List<RagChunkDraft> drafts = extractor.extract(code, metadata(1000));

        assertEquals(2, drafts.size());
        assertEquals("", drafts.get(0).metadata().get("package"));
        assertEquals("", drafts.get(0).metadata().get("symbol"));
        assertEquals(1, drafts.get(0).metadata().get("line_start"));
        assertEquals(1, drafts.get(0).metadata().get("line_end"));
        assertEquals("recover", drafts.get(1).metadata().get("symbol"));
        assertEquals(2, drafts.get(1).metadata().get("line_start"));
        assertEquals(5, drafts.get(1).metadata().get("line_end"));
        assertTrue(drafts.get(0).text().contains("P".repeat(100)));
        assertTrue(drafts.get(1).text().contains("recover"));
    }

    @Test
    void shouldPreserveLengthFlushAndContinuousMaterializedIndexes() {
        String code = "package demo;\n"
                + "String payload = \"" + "X".repeat(1100) + "\";\n"
                + "public void after() {}\n";

        List<RagChunkDraft> drafts = extractor.extract(code, metadata(1000));
        List<RagDocument> documents = materializer.materialize(drafts);

        assertEquals(2, drafts.size());
        assertEquals(1, drafts.get(0).metadata().get("line_start"));
        assertEquals(2, drafts.get(0).metadata().get("line_end"));
        assertEquals(3, drafts.get(1).metadata().get("line_start"));
        assertEquals(4, drafts.get(1).metadata().get("line_end"));
        assertEquals("after", drafts.get(1).metadata().get("symbol"));
        assertEquals(List.of(0, 1), documents.stream()
                .map(document -> (Integer) document.metadata().get("chunk_index"))
                .toList());
    }

    @Test
    void shouldNormalizeBomAndCrLfWithoutChangingCodeEvidence() {
        List<RagChunkDraft> drafts = extractor.extract(
                "\uFEFFpackage demo;\r\npublic class App {}\r\n",
                metadata(3000));

        assertEquals(1, drafts.size());
        assertEquals("demo", drafts.get(0).metadata().get("package"));
        assertEquals("App", drafts.get(0).metadata().get("symbol"));
        assertTrue(drafts.get(0).text().startsWith("package demo;\npublic class App {}"));
    }

    private Map<String, Object> metadata(int maxSegmentChars) {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "LockService.java",
                "max_segment_chars", maxSegmentChars,
                "hard_split_overlap_chars", 0);
    }
}
