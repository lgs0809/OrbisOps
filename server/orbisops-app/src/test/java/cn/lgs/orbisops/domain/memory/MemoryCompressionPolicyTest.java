package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionPlan;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionProjection;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionSummary;
import cn.lgs.orbisops.domain.memory.service.MemoryCompressionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCompressionPolicyTest {

    private final MemoryCompressionPolicy policy = new MemoryCompressionPolicy(new MemoryContentHashPolicy());

    @Test
    void repeatedCompressionKeepsEarlyConstraintAndUnfinishedWork() {
        var first = policy.ruleSummary(List.of(
                message("user", "只读排查，禁止重启订单服务", "t1"),
                message("assistant", "待确认连接池等待与慢 SQL 的关联", "t2")));
        var inherited = new ColdMemoryMessageSnapshot("session-1", "user-1", "system",
                first.content(), "t3", Map.of("memory_type", "summary"));
        var second = policy.ruleSummary(List.of(inherited,
                message("user", "继续查询日志", "t4"),
                message("assistant", "日志已取得", "t5")));

        assertTrue(second.content().contains("禁止重启订单服务"));
        assertTrue(second.content().contains("待确认连接池等待与慢 SQL 的关联"));
        assertTrue(second.content().contains("日志已取得"));
    }

    @Test
    void planRequiresHistoricalThresholdAndKeepsConfiguredRecentMessages() {
        List<ColdMemoryMessageSnapshot> messages = messages(10);

        MemoryCompressionPlan plan = policy.plan(messages, 6, 3);

        assertTrue(plan.required());
        assertEquals(7, plan.olderMessages().size());
        assertEquals(3, plan.recentMessages().size());
        assertEquals("content-0", plan.olderMessages().get(0).content());
        assertEquals("content-7", plan.recentMessages().get(0).content());
        assertEquals("content-9", plan.recentMessages().get(2).content());
    }

    @Test
    void planUsesMinimumThresholdAndMinimumKeep() {
        assertFalse(policy.plan(messages(3), 0, 0).required());

        MemoryCompressionPlan plan = policy.plan(messages(4), 0, 0);

        assertTrue(plan.required());
        assertEquals(2, plan.olderMessages().size());
        assertEquals(2, plan.recentMessages().size());
    }

    @Test
    void planCapsKeepToAllButOneMessage() {
        MemoryCompressionPlan plan = policy.plan(messages(5), 4, 99);

        assertEquals(1, plan.olderMessages().size());
        assertEquals(4, plan.recentMessages().size());
    }

    @Test
    void ruleSummaryPreservesHistoricalHeaderRangeSectionsAndLimits() {
        List<ColdMemoryMessageSnapshot> messages = List.of(
                message("user", "用户一", "2026-07-21 10:00:00"),
                message("assistant", "结论一", "2026-07-21 10:01:00"),
                message("user", "用户二", "2026-07-21 10:02:00"),
                message("assistant", "结论二", "2026-07-21 10:03:00"),
                message("user", "用户三", "2026-07-21 10:04:00"),
                message("user", "用户四", "2026-07-21 10:05:00"),
                message("user", "用户五", "2026-07-21 10:06:00"));

        MemoryCompressionSummary summary = policy.ruleSummary(messages);

        assertEquals("context_compressor", summary.source());
        assertTrue(summary.content().startsWith("会话压缩摘要：共压缩 7 条历史消息，时间范围 2026-07-21 10:00:00 至 2026-07-21 10:06:00。"));
        assertTrue(summary.content().contains("用户关注：\n- 用户一\n- 用户二\n- 用户三\n- 用户四"));
        assertFalse(summary.content().contains("用户五"));
        assertTrue(summary.content().contains("已有结论：\n- 结论一\n- 结论二"));
    }

    @Test
    void ruleSummaryAbbreviatesUserAndAssistantSignals() {
        MemoryCompressionSummary summary = policy.ruleSummary(List.of(
                message("user", "u".repeat(121), "start"),
                message("assistant", "a".repeat(161), "end")));

        assertTrue(summary.content().contains("u".repeat(120) + "..."));
        assertTrue(summary.content().contains("a".repeat(160) + "..."));
    }

    @Test
    void projectionBuildsHistoricalSystemSummaryReplacementAndColdItem() {
        MemoryCompressionPlan plan = new MemoryCompressionPlan(
                List.of(message("user", "old-1", "t1"), message("assistant", "old-2", "t2")),
                List.of(message("user", "recent-1", "t3"), message("assistant", "recent-2", "t4")));

        MemoryCompressionProjection projection = policy.project(
                "session-1",
                "user-1",
                plan,
                new MemoryCompressionSummary("压缩摘要", "llm_context_compressor"),
                "2026-07-21 11:00:00");

        assertEquals(3, projection.replacementMessages().size());
        ColdMemoryMessageSnapshot summaryMessage = projection.replacementMessages().get(0);
        assertEquals("system", summaryMessage.role());
        assertEquals("压缩摘要", summaryMessage.content());
        assertEquals("summary", summaryMessage.metadata().get("memory_type"));
        assertEquals("ops_context_compressor", summaryMessage.metadata().get("source"));
        assertEquals("recent-1", projection.replacementMessages().get(1).content());
        assertEquals("summary", projection.summaryItem().memoryType());
        assertEquals(BigDecimal.valueOf(0.86D), projection.summaryItem().importance());
        assertEquals("[\"summary\",\"conversation\"]", projection.summaryItem().tagsJson());
        assertEquals("system", projection.summaryItem().sourceMessageRole());
        assertEquals("llm_context_compressor", projection.summaryItem().metadata().get("source"));
        assertEquals(2, projection.summaryItem().metadata().get("compressedMessages"));
        assertFalse(projection.summaryItem().sourceMessageHash().isBlank());
    }

    @Test
    void projectionRejectsMissingPlanOrSummary() {
        assertTrue(policy.project("s", "u", MemoryCompressionPlan.none(),
                new MemoryCompressionSummary("summary", "source"), "now").replacementMessages().isEmpty());
        assertNull(policy.project("s", "u", MemoryCompressionPlan.none(),
                new MemoryCompressionSummary("summary", "source"), "now").summaryItem());
        assertNull(policy.project("s", "u", new MemoryCompressionPlan(
                        List.of(message("user", "old", "t1")), List.of()),
                new MemoryCompressionSummary(" ", "source"), "now").summaryItem());
    }

    @Test
    void trimContextPreservesHistoricalHeadTailAndMetadataMarker() {
        String context = "head-" + "x".repeat(4000) + "-tail";

        String trimmed = policy.trimContext(context, 1000);

        assertTrue(trimmed.length() < context.length());
        assertTrue(trimmed.startsWith("head-"));
        assertTrue(trimmed.endsWith("-tail"));
        assertTrue(trimmed.contains("memory context compressed"));
        assertTrue(trimmed.contains("originalChars=" + context.length()));
        assertEquals("short", policy.trimContext("short", 1000));
    }

    private List<ColdMemoryMessageSnapshot> messages(int count) {
        List<ColdMemoryMessageSnapshot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(message(i % 2 == 0 ? "user" : "assistant", "content-" + i, "time-" + i));
        }
        return result;
    }

    private ColdMemoryMessageSnapshot message(String role, String content, String createdAt) {
        return new ColdMemoryMessageSnapshot(
                "session-1",
                "user-1",
                role,
                content,
                createdAt,
                Map.of("turn", content));
    }
}
