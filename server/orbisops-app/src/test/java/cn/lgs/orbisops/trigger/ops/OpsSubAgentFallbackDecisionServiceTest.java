package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSubAgentFallbackDecisionServiceTest {

    private final OpsSubAgentFallbackDecisionService service =
            new OpsSubAgentFallbackDecisionService();

    @Test
    void mapsRuntimeFilterAndRequestFieldsIntoQueryFallback() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .question("检查 traceId trace-123")
                .rangeMinutes(45)
                .promWindow("15m")
                .includeRecentLogs(true)
                .build();

        OpsSubAgentDecision decision = service.query(
                "elasticsearch",
                request,
                OpsQuestionContext.from(request.getQuestion()),
                "模型不可用");

        assertFalse(decision.llmGenerated());
        assertEquals("只读兜底策略：模型不可用", decision.reason());
        assertEquals(Integer.valueOf(45), decision.rangeMinutes());
        assertEquals("15m", decision.promWindow());
        assertEquals(Boolean.TRUE, decision.includeRecentLogs());
        assertEquals("hybrid", decision.retrievalMode());
        assertEquals(Boolean.TRUE, decision.requireExactFilters());
    }

    @Test
    void noRuntimeFilterUsesAutoAndKeepsNullableRequestFields() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .question("检查当前健康状态")
                .build();

        OpsSubAgentDecision decision = service.query(
                "prometheus",
                request,
                OpsQuestionContext.from(request.getQuestion()),
                "默认");

        assertEquals("auto", decision.retrievalMode());
        assertEquals(Boolean.FALSE, decision.requireExactFilters());
        assertNull(decision.rangeMinutes());
        assertNull(decision.promWindow());
        assertNull(decision.includeRecentLogs());
    }

    @Test
    void reviewMappingPreservesDtoFieldsAndNullLists() {
        OpsAgentReview fallback = new OpsAgentReview(
                false,
                "BLOCKED",
                "被阻塞",
                null,
                null,
                null,
                null);

        OpsAgentReview review = service.review(fallback, "连接失败");

        assertFalse(review.llmGenerated());
        assertEquals("BLOCKED", review.status());
        assertEquals("被阻塞", review.summary());
        assertEquals(List.of("LLM 子 Agent 复盘不可用：连接失败"), review.gaps());
        assertEquals(List.of(
                "已保留真实查询 observation，并使用确定性规则判断证据状态。"),
                review.suggestedAdjustments());
        assertNull(review.shouldRetry());
        assertNull(review.confidence());
        review.gaps().add("可追加");
        assertTrue(review.gaps().contains("可追加"));
    }
}
