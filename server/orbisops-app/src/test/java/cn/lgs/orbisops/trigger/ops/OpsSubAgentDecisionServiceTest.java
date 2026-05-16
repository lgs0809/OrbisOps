package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsSubAgentDecisionServiceTest {

    @Test
    void disabledLlmKeepsLegacyDeterministicFallbackAndReviewIdentity() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        OpsSubAgentDecisionService service = new OpsSubAgentDecisionService(llmClient);
        OpsAgentReview fallbackReview = fallbackReview();

        OpsSubAgentDecision decision = service.decide(
                "elasticsearch",
                task("elasticsearch"),
                request(),
                OpsQuestionContext.from("检查 traceId trace-123"),
                "profile");
        OpsAgentReview review = service.review(
                "elasticsearch",
                task("elasticsearch"),
                request(),
                OpsQuestionContext.from("检查 traceId trace-123"),
                "observation",
                fallbackReview);

        assertFalse(decision.llmGenerated());
        assertEquals("hybrid", decision.retrievalMode());
        assertEquals(Boolean.TRUE, decision.requireExactFilters());
        assertTrue(decision.reason().contains("只读兜底策略"));
        assertSame(fallbackReview, review);
        verifyNoInteractions(llmClient);
    }

    @Test
    void nullLlmJsonUsesDistinctThinkAndReviewFallbackMessages() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(null);
        OpsSubAgentDecisionService service = new OpsSubAgentDecisionService(
                llmClient,
                new OpsSubAgentDecisionSettings(true, true));

        OpsSubAgentDecision decision = service.decide(
                "prometheus",
                task("prometheus"),
                request(),
                OpsQuestionContext.from("检查当前指标"),
                "profile");
        OpsAgentReview review = service.review(
                "prometheus",
                task("prometheus"),
                request(),
                OpsQuestionContext.from("检查当前指标"),
                "真实 observation",
                fallbackReview());

        assertFalse(decision.llmGenerated());
        assertTrue(decision.reason().contains("LLM THINK 未返回 JSON"));
        assertFalse(review.llmGenerated());
        assertTrue(review.gaps().get(0).contains("LLM REVIEW 未返回 JSON"));
        assertEquals("NOT_FOUND", review.status());
        assertEquals(Boolean.FALSE, review.shouldRetry());
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(source + "-agent")
                .goal("查证据")
                .reason("补齐证据")
                .build();
    }

    private OpsAgentRunRequestDTO request() {
        return OpsAgentRunRequestDTO.builder()
                .question("检查问题")
                .rangeMinutes(30)
                .promWindow("5m")
                .includeRecentLogs(false)
                .subAgentMaxIterations(2)
                .build();
    }

    private OpsAgentReview fallbackReview() {
        return new OpsAgentReview(
                false,
                "NOT_FOUND",
                "没有命中",
                List.of("缺少证据"),
                List.of("扩大范围"),
                false,
                0.3D);
    }
}
