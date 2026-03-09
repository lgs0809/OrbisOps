package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsInvestigationObservationRoutingServiceTest {

    @Test
    void disabledReflectionUsesDeterministicFollowUpOnly() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        OpsInvestigationObservationRoutingService service = service(llmClient);

        OpsInvestigationObservationRoutingService.Decision decision =
                service.decide(input(false));

        assertEquals(List.of("prometheus"), decision.tasks().stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .toList());
        assertFalse(decision.clearQueue());
        assertTrue(decision.notes().get(0).contains("追加 prometheus-agent"));
        verifyNoInteractions(llmClient);
    }

    @Test
    void invalidReflectionKeepsFallbackNoteThenRunsDeterministicPolicy() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(null);
        OpsInvestigationObservationRoutingService service = service(llmClient);

        OpsInvestigationObservationRoutingService.Decision decision =
                service.decide(input(true));

        assertEquals("prometheus", decision.tasks().get(0).getSource());
        assertTrue(decision.notes().get(0).contains("未返回 JSON"));
        assertTrue(decision.notes().get(1).contains("追加 prometheus-agent"));
        assertFalse(decision.clearQueue());
    }

    @Test
    void validStopDecisionSuppressesDeterministicFallback() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"decision":"stop","note":"证据已经足够","addTasks":[]}
                        """));
        OpsInvestigationObservationRoutingService service = service(llmClient);

        OpsInvestigationObservationRoutingService.Decision decision =
                service.decide(input(true));

        assertTrue(decision.tasks().isEmpty());
        assertTrue(decision.clearQueue());
        assertEquals(List.of(
                "LLM主 Agent 复盘：证据已经足够",
                "LLM主 Agent 判断当前证据已足够，停止继续派发。"), decision.notes());
    }

    private OpsInvestigationObservationRoutingService service(OpsAgentLlmClient llmClient) {
        return new OpsInvestigationObservationRoutingService(
                new OpsInvestigationReflectionService(llmClient),
                new OpsInvestigationFollowUpService());
    }

    private OpsInvestigationObservationRoutingService.Input input(boolean reflectionEnabled) {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .question("检查当前指标")
                .rangeMinutes(10)
                .promWindow("5m")
                .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("RUNTIME_INVESTIGATION")
                        .tasks(List.of())
                        .conditionalTasks(List.of())
                        .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO latest =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .agent("es-log-agent")
                        .status("INSUFFICIENT")
                        .summary("日志证据不足")
                        .evidence(List.of())
                        .gaps(List.of("缺少指标"))
                        .suggestedAdjustments(List.of())
                        .build();
        return new OpsInvestigationObservationRoutingService.Input(
                request,
                plan,
                latest,
                List.of(latest),
                Set.of("elasticsearch", "prometheus"),
                Set.of("elasticsearch"),
                Set.of(),
                OpsQuestionContext.from(request.getQuestion()),
                reflectionEnabled,
                true);
    }
}
