package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OpsSubAgentDecisionProtocolServiceTest {

    @Test
    void validThinkJsonProducesTypedDecisionAndSourceSkill() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "reason":"扩大窗口补查",
                          "rangeMinutes":60,
                          "promWindow":"15m",
                          "includeRecentLogs":true,
                          "retrievalMode":"hybrid",
                          "queryFocus":"trace 相关错误",
                          "requireExactFilters":true,
                          "expectedEvidence":["代表性日志","错误聚合"]
                        }
                        """));
        OpsSubAgentDecisionProtocolService service =
                new OpsSubAgentDecisionProtocolService(llmClient);

        OpsSubAgentDecisionProtocolService.DecisionAttempt attempt =
                service.decide(decisionInput("elasticsearch", "上一轮无命中"));

        assertTrue(attempt.valid());
        assertEquals(OpsSubAgentDecisionProtocolService.Status.VALID, attempt.status());
        assertTrue(attempt.decision().llmGenerated());
        assertEquals(Integer.valueOf(60), attempt.decision().rangeMinutes());
        assertEquals("15m", attempt.decision().promWindow());
        assertEquals("hybrid", attempt.decision().retrievalMode());
        assertEquals(List.of("代表性日志", "错误聚合"), attempt.decision().expectedEvidence());
        verify(llmClient).chatJsonObjectWithEagerSkillContext(
                org.mockito.ArgumentMatchers.eq("elasticsearch-react-think"),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(List.of("es-log-agent")));
    }

    @Test
    void missingOptionalThinkFieldsPreserveFallbackValuesAndListIdentity() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"reason":"沿用默认值","queryFocus":"原过滤条件"}
                        """));
        OpsSubAgentDecision fallback = fallbackDecision();
        OpsSubAgentDecisionProtocolService service =
                new OpsSubAgentDecisionProtocolService(llmClient);
        OpsSubAgentDecisionProtocolService.DecisionInput input = decisionInput("rag", "");
        input = new OpsSubAgentDecisionProtocolService.DecisionInput(
                input.source(),
                input.task(),
                input.request(),
                input.questionContext(),
                input.datasourceProfile(),
                input.previousObservation(),
                fallback);

        OpsSubAgentDecision decision = service.decide(input).decision();

        assertEquals(fallback.rangeMinutes(), decision.rangeMinutes());
        assertEquals(fallback.promWindow(), decision.promWindow());
        assertEquals(fallback.includeRecentLogs(), decision.includeRecentLogs());
        assertEquals(fallback.retrievalMode(), decision.retrievalMode());
        assertEquals(fallback.requireExactFilters(), decision.requireExactFilters());
        assertSame(fallback.expectedEvidence(), decision.expectedEvidence());
    }

    @Test
    void nullThinkJsonAndInvalidSchemaRemainDistinctProtocolStatuses() {
        OpsAgentLlmClient noJsonClient = mock(OpsAgentLlmClient.class);
        when(noJsonClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(null);
        OpsSubAgentDecisionProtocolService.DecisionAttempt noJson =
                new OpsSubAgentDecisionProtocolService(noJsonClient)
                        .decide(decisionInput("prometheus", ""));

        assertEquals(OpsSubAgentDecisionProtocolService.Status.NO_JSON, noJson.status());
        assertNull(noJson.decision());
        verify(noJsonClient).chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection());
        verifyNoMoreInteractions(noJsonClient);

        OpsAgentLlmClient invalidClient = mock(OpsAgentLlmClient.class);
        when(invalidClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"reason":"缺少 queryFocus","rangeMinutes":30}
                        """));
        OpsSubAgentDecisionProtocolService.DecisionAttempt invalid =
                new OpsSubAgentDecisionProtocolService(invalidClient)
                        .decide(decisionInput("prometheus", ""));

        assertEquals(OpsSubAgentDecisionProtocolService.Status.INVALID_SCHEMA, invalid.status());
        assertFalse(invalid.validationErrors().isEmpty());
        verify(invalidClient).rejectDegradation(
                org.mockito.ArgumentMatchers.eq("prometheus-react-think"),
                org.mockito.ArgumentMatchers.contains("JSON 校验失败"));
    }

    @Test
    void validReviewJsonProducesTypedReviewAndPromptContainsObservation() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "status":"INSUFFICIENT",
                          "summary":"证据仍不足",
                          "gaps":["缺少长窗口样本"],
                          "suggestedAdjustments":["扩大到 60 分钟"],
                          "shouldRetry":true,
                          "confidence":0.65
                        }
                        """));
        OpsSubAgentDecisionProtocolService service =
                new OpsSubAgentDecisionProtocolService(llmClient);

        OpsSubAgentDecisionProtocolService.ReviewAttempt attempt =
                service.review(reviewInput("prometheus", "真实 observation"));

        assertTrue(attempt.valid());
        assertEquals("INSUFFICIENT", attempt.review().status());
        assertEquals(Boolean.TRUE, attempt.review().shouldRetry());
        assertEquals(0.65D, attempt.review().confidence());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chatJsonObjectWithEagerSkillContext(
                org.mockito.ArgumentMatchers.eq("prometheus-react-review"),
                anyString(),
                prompt.capture(),
                org.mockito.ArgumentMatchers.eq(List.of("prometheus-agent")));
        assertTrue(prompt.getValue().contains("真实 observation"));
    }

    private OpsSubAgentDecisionProtocolService.DecisionInput decisionInput(
            String source,
            String previousObservation) {
        return new OpsSubAgentDecisionProtocolService.DecisionInput(
                source,
                task(source),
                request(),
                OpsQuestionContext.from("检查 traceId trace-123 的异常"),
                "datasource profile",
                previousObservation,
                fallbackDecision());
    }

    private OpsSubAgentDecisionProtocolService.ReviewInput reviewInput(
            String source,
            String observation) {
        return new OpsSubAgentDecisionProtocolService.ReviewInput(
                source,
                task(source),
                request(),
                OpsQuestionContext.from("检查接口延迟"),
                observation,
                new OpsAgentReview(
                        false,
                        "NOT_FOUND",
                        "fallback summary",
                        List.of("fallback gap"),
                        List.of("fallback adjustment"),
                        false,
                        0.2D));
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(source + "-agent")
                .goal("查询真实证据")
                .reason("补齐证据")
                .priority(1)
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

    private OpsSubAgentDecision fallbackDecision() {
        return new OpsSubAgentDecision(
                false,
                "fallback",
                30,
                "5m",
                false,
                "auto",
                "fallback focus",
                false,
                List.of("fallback evidence"));
    }
}
