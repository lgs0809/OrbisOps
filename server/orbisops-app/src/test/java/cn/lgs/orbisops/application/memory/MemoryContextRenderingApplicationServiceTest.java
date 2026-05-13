package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryContextRenderingApplicationServiceTest {

    private final MemoryContextRenderingApplicationService service =
            new MemoryContextRenderingApplicationService();

    @Test
    void rendersThreeTypedSectionsInSelectionOrder() {
        MemoryContextRenderingResult result = service.render(new MemoryContextRenderingRequest(
                List.of(context("ctx-1", "Project title", "Project summary")),
                List.of(item("PROJECT_CONTEXT", "durable item", BigDecimal.valueOf(0.9))),
                List.of(message("user", "recent message")),
                3,
                3,
                3,
                4000));

        assertTrue(result.context().contains("### 用户/项目长期语境"));
        assertTrue(result.context().contains("[PROJECT_CONTEXT] Project title: Project summary"));
        assertTrue(result.context().contains("### 运维上下文记忆"));
        assertTrue(result.context().contains("importance=0.9"));
        assertTrue(result.context().contains("### 运维对话上下文"));
        assertTrue(result.context().contains("user @ 2026-07-21 18:00:00: recent message"));
        assertEquals(1, result.contextMemoryCount());
        assertEquals(1, result.itemCount());
        assertEquals(1, result.messageCount());
        assertFalse(result.truncated());
    }

    @Test
    void enforcesPerSectionLimits() {
        MemoryContextRenderingResult result = service.render(new MemoryContextRenderingRequest(
                List.of(context("ctx-1", "first", "one"), context("ctx-2", "second", "two")),
                List.of(item("FACT", "item-one", BigDecimal.ONE), item("FACT", "item-two", BigDecimal.ONE)),
                List.of(message("user", "message-one"), message("assistant", "message-two")),
                1,
                1,
                1,
                4000));

        assertTrue(result.context().contains("first"));
        assertFalse(result.context().contains("second"));
        assertTrue(result.context().contains("item-one"));
        assertFalse(result.context().contains("item-two"));
        assertTrue(result.context().contains("message-one"));
        assertFalse(result.context().contains("message-two"));
        assertEquals(1, result.contextMemoryCount());
        assertEquals(1, result.itemCount());
        assertEquals(1, result.messageCount());
    }

    @Test
    void abbreviatesLinesAndTrimsOversizedContextWithHeadAndTail() {
        String longText = "head-" + "x".repeat(5000) + "-tail";
        MemoryContextRenderingResult result = service.render(new MemoryContextRenderingRequest(
                List.of(),
                List.of(
                        item("FACT", longText, BigDecimal.ONE),
                        item("FACT", longText + "-second", BigDecimal.ONE),
                        item("FACT", longText + "-third", BigDecimal.ONE)),
                List.of(),
                1,
                3,
                1,
                1200));

        assertTrue(result.context().contains("memory context compressed"));
        assertTrue(result.context().startsWith("### 运维上下文记忆"));
        assertTrue(result.context().contains("..."));
        assertTrue(result.truncated());
    }

    @Test
    void protectedSourceRemainsWholeAndCanonicalTailIsChronological() {
        String constraint = "禁止修改订单服务，" + "完整边界条件".repeat(180) + "；待确认审批范围。";
        var source = new MemoryMessageCandidate("s1", "u1", "user", constraint, "", Map.of("messageSeq", 1L, "memory_type", "protected_source"));
        var later = new MemoryMessageCandidate("s1", "u1", "assistant", "later-answer", "", Map.of("messageSeq", 6L, "memory_type", "conversation_tail"));
        var earlier = new MemoryMessageCandidate("s1", "u1", "user", "earlier-question", "", Map.of("messageSeq", 5L, "memory_type", "conversation_tail"));
        var result = service.render(new MemoryContextRenderingRequest(List.of(), List.of(), List.of(later, earlier, source), 3, 3, 3, 4000));
        assertTrue(result.context().contains(constraint));
        assertTrue(result.context().indexOf("earlier-question") < result.context().indexOf("later-answer"));
        var tooLarge = new MemoryMessageCandidate("s1", "u1", "user", constraint.repeat(3), "", source.metadata());
        assertThrows(MemoryContextIntegrityException.class, () -> service.render(new MemoryContextRenderingRequest(
                List.of(), List.of(), List.of(tooLarge), 1, 1, 1, 1200)));
    }

    @Test
    void emptyInputReturnsEmptyResult() {
        assertEquals(MemoryContextRenderingResult.empty(), service.render(
                new MemoryContextRenderingRequest(List.of(), List.of(), List.of(), 1, 1, 1, 1200)));
        assertEquals(MemoryContextRenderingResult.empty(), service.render(null));
    }

    private ContextMemoryView context(String memoryId, String title, String summary) {
        return new ContextMemoryView(
                memoryId,
                "PROJECT_CONTEXT",
                "PROJECT",
                "demo-project",
                title,
                summary,
                summary,
                "hash",
                Map.of());
    }

    private MemoryItemCandidate item(String type, String content, BigDecimal importance) {
        return new MemoryItemCandidate(
                "s1", "u1", type, content, importance, "[]", "user", "hash", Map.of(),
                "2026-07-21 18:00:00");
    }

    private MemoryMessageCandidate message(String role, String content) {
        return new MemoryMessageCandidate(
                "s1", "u1", role, content, "2026-07-21 18:00:00", Map.of());
    }
}
