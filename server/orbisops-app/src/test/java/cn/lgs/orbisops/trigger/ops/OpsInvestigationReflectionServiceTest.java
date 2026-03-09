package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsInvestigationReflectionServiceTest {

    @Test
    void validContinueFiltersUnavailableExecutedAndQueuedSources() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        JSONObject json = JSON.parseObject("""
                {
                  "decision":"continue",
                  "note":"需要补充两类证据",
                  "addTasks":[
                    {"source":"prometheus","agent":"prometheus-agent","goal":"查指标","reason":"确认影响面","priority":2,"condition":"main reflection"},
                    {"source":"elasticsearch","agent":"es-log-agent","goal":"查日志","reason":"已执行，不应重复","priority":2,"condition":"main reflection"},
                    {"source":"rag","agent":"rag-knowledge-agent","goal":"查知识","reason":"已排队，不应重复","priority":3,"condition":"main reflection"},
                    {"source":"unknown","agent":"unknown-agent","goal":"未知","reason":"未注册","priority":3,"condition":"main reflection"}
                  ]
                }
                """);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(json);
        OpsInvestigationReflectionService service =
                new OpsInvestigationReflectionService(llmClient);

        OpsInvestigationReflectionService.Decision decision = service.reflect(input(
                Set.of("prometheus", "elasticsearch", "rag"),
                Set.of("elasticsearch"),
                Set.of("rag")));

        assertTrue(decision.valid());
        assertFalse(decision.stop());
        assertEquals("需要补充两类证据", decision.note());
        assertEquals(1, decision.tasks().size());
        assertEquals("prometheus", decision.tasks().get(0).getSource());
        assertEquals(2, decision.tasks().get(0).getPriority());
    }

    @Test
    void stopDecisionReturnsNoTasks() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"decision":"stop","note":"证据已足够","addTasks":[]}
                        """));

        OpsInvestigationReflectionService.Decision decision =
                new OpsInvestigationReflectionService(llmClient)
                        .reflect(input(Set.of("prometheus"), Set.of(), Set.of()));

        assertTrue(decision.valid());
        assertTrue(decision.stop());
        assertEquals("证据已足够", decision.note());
        assertTrue(decision.tasks().isEmpty());
    }

    @Test
    void nullJsonFallsBackWithoutPretendingValidReflection() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(null);

        OpsInvestigationReflectionService.Decision decision =
                new OpsInvestigationReflectionService(llmClient)
                        .reflect(input(Set.of("prometheus"), Set.of(), Set.of()));

        assertFalse(decision.valid());
        assertTrue(decision.fallbackNote().contains("未返回 JSON"));
    }

    @Test
    void invalidSchemaDelegatesDegradationPolicyAndFallsBack() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"decision":"invalid","addTasks":[]}
                        """));

        OpsInvestigationReflectionService.Decision decision =
                new OpsInvestigationReflectionService(llmClient)
                        .reflect(input(Set.of("prometheus"), Set.of(), Set.of()));

        assertFalse(decision.valid());
        assertTrue(decision.fallbackNote().contains("JSON 校验失败"));
        verify(llmClient).rejectDegradation(
                org.mockito.ArgumentMatchers.eq("ops-main-agent-reflection"),
                org.mockito.ArgumentMatchers.contains("复盘 JSON 校验失败"));
    }

    @Test
    void promptContainsQuestionFiltersAndOnlyAvailableSources() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"decision":"continue","note":"继续","addTasks":[]}
                        """));
        new OpsInvestigationReflectionService(llmClient).reflect(input(
                Set.of("prometheus", "elasticsearch", "rag"),
                Set.of("elasticsearch"),
                Set.of("rag")));

        org.mockito.ArgumentCaptor<String> prompt =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmClient).chatJsonObject(
                org.mockito.ArgumentMatchers.eq("ops-main-agent-reflection"),
                anyString(),
                prompt.capture(),
                anyCollection());
        assertTrue(prompt.getValue().contains("traceId=trace-123456"));
        assertTrue(prompt.getValue().contains("尚可追加的数据源：prometheus"));
        assertFalse(prompt.getValue().contains("尚可追加的数据源：elasticsearch"));
    }

    private OpsInvestigationReflectionService.Input input(Set<String> registered,
                                                           Set<String> executed,
                                                           Set<String> queued) {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .question("检查 traceId trace-123456 的错误与指标")
                .rangeMinutes(30)
                .promWindow("5m")
                .maxRounds(2)
                .subAgentMaxIterations(2)
                .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("RUNTIME_INVESTIGATION")
                        .reason("检查运行时证据")
                        .tasks(List.of())
                        .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO latest =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .agent("es-log-agent")
                        .status("INSUFFICIENT")
                        .summary("日志证据不足")
                        .evidence(List.of("trace 未命中"))
                        .gaps(List.of("缺少指标"))
                        .suggestedAdjustments(List.of("查询 prometheus"))
                        .build();
        return new OpsInvestigationReflectionService.Input(
                request,
                plan,
                latest,
                List.of(latest),
                registered,
                executed,
                queued,
                OpsQuestionContext.from(request.getQuestion()));
    }
}
