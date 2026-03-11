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

class RagTextMarkupChunkExtractorTest {

    private final RagChunkMaterializer materializer = new RagChunkMaterializer();
    private final RagTextMarkupChunkExtractor extractor = new RagTextMarkupChunkExtractor(materializer);

    @Test
    void shouldPreserveHeadingHierarchyImageOrderAndFenceExclusion() {
        String markdown = """
                # Root

                Intro evidence.
                ![ topology ](<./images/flow.png>)

                ## Child ###

                Child evidence.

                ```markdown
                ![ignored](inside-fence.png)
                ```
                """;

        List<RagChunkDraft> drafts = extractor.extractMarkdown(markdown, metadata(), "markdown-heading");

        assertEquals(3, drafts.size());
        RagChunkDraft image = drafts.get(0);
        RagChunkDraft root = drafts.get(1);
        RagChunkDraft child = drafts.get(2);

        assertEquals(RagChunkDraft.Boundary.EXACT, image.boundary());
        assertEquals("markdown-image-reference", image.metadata().get("chunk_strategy"));
        assertEquals("image_reference", image.metadata().get("chunk_type"));
        assertEquals("./images/flow.png", image.metadata().get("image_path"));
        assertEquals("topology", image.metadata().get("image_alt"));
        assertEquals("Root", image.metadata().get("section_title"));
        assertEquals("Root", image.metadata().get("heading_path"));
        assertFalse(drafts.stream().anyMatch(draft -> "inside-fence.png".equals(draft.metadata().get("image_path"))));

        assertEquals(RagChunkDraft.Boundary.STRUCTURE_BOUNDED, root.boundary());
        assertEquals(0, root.metadata().get("section_index"));
        assertEquals("Root", root.metadata().get("section_title"));
        assertEquals("Root", root.metadata().get("heading_path"));
        assertTrue(root.text().contains("# Root"));
        assertTrue(root.text().contains("![ topology ](<./images/flow.png>)"));

        assertEquals(1, child.metadata().get("section_index"));
        assertEquals("Child", child.metadata().get("section_title"));
        assertEquals("Root > Child", child.metadata().get("heading_path"));
        assertTrue(child.text().contains("inside-fence.png"));

        List<RagDocument> documents = materializer.materialize(drafts);
        assertEquals(List.of(0, 1, 2), documents.stream()
                .map(document -> (Integer) document.metadata().get("chunk_index"))
                .toList());
        assertEquals("markdown-image-reference", documents.get(0).metadata().get("chunk_strategy"));
        assertEquals("markdown-heading", documents.get(1).metadata().get("chunk_strategy"));
        assertEquals("markdown-heading", documents.get(2).metadata().get("chunk_strategy"));
    }

    @Test
    void shouldPreserveRootSectionBeforeFirstHeading() {
        String markdown = """
                Preamble evidence.

                # Recovery

                Recovery steps.
                """;

        List<RagChunkDraft> drafts = extractor.extractMarkdown(markdown, metadata(), "markdown-heading");

        assertEquals(2, drafts.size());
        assertEquals("ROOT", drafts.get(0).metadata().get("section_title"));
        assertEquals("ROOT", drafts.get(0).metadata().get("heading_path"));
        assertEquals(0, drafts.get(0).metadata().get("section_index"));
        assertEquals("Recovery", drafts.get(1).metadata().get("section_title"));
        assertEquals("Recovery", drafts.get(1).metadata().get("heading_path"));
        assertEquals(1, drafts.get(1).metadata().get("section_index"));
    }

    @Test
    void shouldConvertHtmlUsingExistingCompatibilityChain() {
        String html = """
                <script>danger()</script>
                <!-- comment -->
                <h1>Runbook</h1>
                <p>Check &amp; retry<br/>Now</p>
                <ul><li>First</li><li>Second</li></ul>
                <pre>select &lt; 3;</pre>
                <table><tr><th>code</th><td>action</td></tr></table>
                """;

        List<RagDocument> documents = materializer.materialize(extractor.extractHtml(html, metadata()));

        assertFalse(documents.isEmpty());
        String text = documents.stream().map(RagDocument::text).reduce("", (left, right) -> left + "\n" + right);
        assertFalse(text.contains("danger"));
        assertFalse(text.contains("comment"));
        assertTrue(text.contains("# Runbook"));
        assertTrue(text.contains("Check & retry"));
        assertTrue(text.contains("- First"));
        assertTrue(text.contains("```"));
        assertTrue(text.contains("select < 3;"));
        assertTrue(text.contains("| code | action |"));
        assertTrue(documents.stream().allMatch(document ->
                "html-to-markdown".equals(document.metadata().get("chunk_strategy"))));
    }

    @Test
    void shouldKeepMarkdownSniffingLimitedToFirstEightyLines() {
        String lateHeading = String.join("\n", Collections.nCopies(80, "plain")) + "\n# Too late";
        String boundaryHeading = String.join("\n", Collections.nCopies(79, "plain")) + "\n# Included";

        assertFalse(extractor.looksLikeMarkdown(lateHeading));
        assertTrue(extractor.looksLikeMarkdown(boundaryHeading));
        assertTrue(extractor.looksLikeMarkdown("plain\n```java\ncode\n```"));
        assertFalse(extractor.looksLikeMarkdown("plain\n~~~java\ncode\n~~~"));
    }

    @Test
    void shouldKeepBlankMarkdownAsNonMaterializedDraft() {
        List<RagChunkDraft> drafts = extractor.extractMarkdown(" \r\n\t ", metadata(), "markdown-heading");

        assertEquals(1, drafts.size());
        assertTrue(materializer.materialize(drafts).isEmpty());
    }

    private Map<String, Object> metadata() {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "runbook.md",
                "max_segment_chars", 1000,
                "hard_split_overlap_chars", 100);
    }
}
