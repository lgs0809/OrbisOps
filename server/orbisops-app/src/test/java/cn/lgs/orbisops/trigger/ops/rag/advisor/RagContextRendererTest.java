package cn.lgs.orbisops.trigger.ops.rag.advisor;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextRendererTest {

    private final RagContextRenderer renderer = new RagContextRenderer();

    @Test
    void shouldRenderTemplateJoinDocumentsAndPreserveRequestParameters() {
        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("tenant", "ops");
        List<Document> documents = List.of(
                document("first", "第一段证据", Map.of("source", "first.md")),
                document("second", "第二段证据", Map.of("source", "second.md")));
        RagRetrievalPlan plan = plan(1200);

        RagContextRenderer.RenderedContext rendered = renderer.render(
                "锁单失败怎么排查？",
                requestContext,
                documents,
                plan);

        assertTrue(rendered.advisedUserText().startsWith("锁单失败怎么排查？" + System.lineSeparator()));
        assertTrue(rendered.advisedUserText().contains("{question_answer_context}"));
        assertTrue(rendered.advisedUserText().contains("If the answer is not in the context"));
        assertEquals("第一段证据" + System.lineSeparator() + "第二段证据", rendered.documentContext());
        assertEquals("ops", rendered.parameters().get("tenant"));
        assertEquals(rendered.documentContext(), rendered.parameters().get("question_answer_context"));
        assertSame(plan, rendered.parameters().get("qa_retrieval_plan"));
        JSONObject serialized = JSONObject.parseObject(rendered.serializedParameters());
        assertEquals("ops", serialized.getString("tenant"));
        assertEquals(rendered.documentContext(), serialized.getString("question_answer_context"));
        assertEquals(2, rendered.limitedDocuments().size());
        assertFalse(requestContext.containsKey("question_answer_context"));
        assertFalse(requestContext.containsKey("qa_retrieval_plan"));
    }

    @Test
    void shouldFilterBlankDocumentsAndTruncateLastDocumentWhenRemainingBudgetExceedsThreshold() {
        Document originalLong = document("long", "b".repeat(500), Map.of("source", "long.md"));
        List<Document> documents = List.of(
                document("blank", " \t\n", Map.of()),
                document("first", "a".repeat(100), Map.of("source", "first.md")),
                originalLong,
                document("after", "should not be reached", Map.of()));

        List<Document> limited = renderer.limitDocuments(documents, 450);

        assertEquals(2, limited.size());
        assertEquals("first", limited.get(0).getId());
        assertEquals("long", limited.get(1).getId());
        assertEquals(350, limited.get(1).getText().length());
        assertEquals("long.md", limited.get(1).getMetadata().get("source"));
        assertEquals(500, originalLong.getText().length());
        assertThrows(UnsupportedOperationException.class, () -> limited.add(originalLong));
    }

    @Test
    void shouldSkipOversizedDocumentAtExactThresholdAndAllowLaterShortDocument() {
        List<Document> documents = List.of(
                document("first", "a".repeat(100), Map.of()),
                document("oversized", "b".repeat(500), Map.of()),
                document("later", "c".repeat(200), Map.of()));

        List<Document> limited = renderer.limitDocuments(documents, 400);

        assertEquals(List.of("first", "later"), limited.stream().map(Document::getId).toList());
        assertEquals(300, limited.stream().mapToInt(document -> document.getText().length()).sum());
    }

    @Test
    void shouldStopAtExhaustedBudgetAndPreserveExactFit() {
        List<Document> documents = List.of(
                document("first", "a".repeat(200), Map.of()),
                document("second", "b".repeat(200), Map.of()),
                document("third", "c", Map.of()));

        List<Document> limited = renderer.limitDocuments(documents, 400);

        assertEquals(List.of("first", "second"), limited.stream().map(Document::getId).toList());
    }

    @Test
    void shouldHandleNullContextAndDocumentsWithoutMutatingAnything() {
        RagContextRenderer.RenderedContext rendered = renderer.render(
                null,
                null,
                null,
                plan(100));

        assertTrue(rendered.advisedUserText().startsWith("null" + System.lineSeparator()));
        assertEquals("", rendered.documentContext());
        assertEquals("", rendered.parameters().get("question_answer_context"));
        assertEquals(List.of(), rendered.limitedDocuments());
    }

    @Test
    void shouldReturnEmptyWhenBudgetIsNotPositive() {
        List<Document> documents = List.of(document("first", "content", Map.of()));

        assertEquals(List.of(), renderer.limitDocuments(documents, 0));
        assertEquals(List.of(), renderer.limitDocuments(documents, -1));
    }

    private Document document(String id, String text, Map<String, Object> metadata) {
        return new Document(id, text, metadata);
    }

    private RagRetrievalPlan plan(int maxContextChars) {
        return new RagRetrievalPlan(
                "hybrid",
                5,
                5,
                3,
                maxContextChars,
                false,
                "none",
                null,
                null,
                null,
                5,
                3,
                1200);
    }
}
