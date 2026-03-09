package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OpsMainAgentPlanningProtocolServiceTest {

    @Test
    void validPlannerJsonProducesTypedPlanAndForwardsSkills() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent":"METRIC_FIRST_INVESTIGATION",
                          "reason":"查询指标",
                          "changeRequested":true,
                          "changeIntent":"调整告警",
                          "tasks":[{"routeKey":"metrics","agent":"prometheus-agent","goal":"检查实例","reason":"需要实时指标","priority":1,"condition":null}],
                          "conditionalTasks":[],
                          "skippedTasks":[]
                        }
                        """));
        OpsMainAgentPlanningProtocolService service = service(llmClient);

        OpsMainAgentPlanningProtocolService.Attempt attempt = service.plan(
                new OpsMainAgentPlanningProtocolService.PlanInput(
                        request("检查实例指标"),
                        OpsQuestionContext.from("检查实例指标"),
                        fallback(),
                        false,
                        "",
                        "prometheus: metrics",
                        sources(),
                        List.of("runtime-ops")));

        assertTrue(attempt.valid());
        assertEquals(OpsMainAgentPlanningProtocolService.Status.VALID, attempt.status());
        assertEquals(OpsMainAgentPlanner.SOURCE_PROM, attempt.plan().getTasks().get(0).getSource());
        assertEquals(Boolean.TRUE, attempt.plan().getChangeRequested());
        assertEquals("调整告警", attempt.plan().getChangeIntent());
        verify(llmClient).chatJsonObjectWithEagerSkillContext(
                anyString(),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(List.of("runtime-ops")));
    }

    @Test
    void invalidPlannerSchemaIsReportedAsProtocolStatus() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {"intent":"BAD","reason":"invalid","tasks":[],"conditionalTasks":[],"skippedTasks":[]}
                        """));
        OpsMainAgentPlanningProtocolService service = service(llmClient);

        OpsMainAgentPlanningProtocolService.Attempt attempt = service.plan(
                new OpsMainAgentPlanningProtocolService.PlanInput(
                        request("检查指标"),
                        OpsQuestionContext.from("检查指标"),
                        fallback(),
                        false,
                        "",
                        "catalog",
                        sources(),
                        List.of()));

        assertFalse(attempt.valid());
        assertEquals(OpsMainAgentPlanningProtocolService.Status.INVALID_SCHEMA, attempt.status());
        assertNull(attempt.plan());
        verify(llmClient).rejectDegradation(
                org.mockito.ArgumentMatchers.eq("ops-main-agent-planner"),
                org.mockito.ArgumentMatchers.contains("JSON 校验失败"));
    }

    @Test
    void nullReplanJsonKeepsDistinctNoJsonStatus() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(null);
        OpsMainAgentPlanningProtocolService service = service(llmClient);

        OpsMainAgentPlanningProtocolService.Attempt attempt = service.replan(
                replanInput(List.of(), false, "", Set.of()));

        assertFalse(attempt.valid());
        assertEquals(OpsMainAgentPlanningProtocolService.Status.NO_JSON, attempt.status());
        assertNull(attempt.plan());
        verify(llmClient).chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection());
        verifyNoMoreInteractions(llmClient);
    }

    @Test
    void graphScopedPlanKeepsDynamicRouteKeyAndReplanPromptShowsRemainingSources() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent":"REPLAN_CONTINUE",
                          "reason":"补查自定义节点",
                          "tasks":[{"routeKey":"custom-diagnosis","agent":"custom-agent","goal":"补查","reason":"有信息增益","priority":1,"condition":"main-replan"}],
                          "conditionalTasks":[],
                          "skippedTasks":[]
                        }
                        """));
        OpsMainAgentPlanningProtocolService service = service(llmClient);
        OpsAnalysisResponseDTO.InvestigationResultDTO esResult =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_ES)
                        .agent("es-log-agent")
                        .status("INSUFFICIENT")
                        .summary("需要补查")
                        .gaps(List.of("缺少自定义证据"))
                        .suggestedAdjustments(List.of("custom-diagnosis"))
                        .build();

        OpsMainAgentPlanningProtocolService.Attempt attempt = service.replan(
                replanInput(
                        List.of(esResult),
                        true,
                        "{\"routers\":[]}",
                        new LinkedHashSet<>(List.of(
                                OpsMainAgentPlanner.SOURCE_ES,
                                "custom-diagnosis"))));

        assertTrue(attempt.valid());
        assertEquals("custom-diagnosis", attempt.plan().getTasks().get(0).getSource());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chatJsonObjectWithEagerSkillContext(
                org.mockito.ArgumentMatchers.eq("ops-main-agent-replanner"),
                anyString(),
                prompt.capture(),
                anyCollection());
        assertTrue(prompt.getValue().contains("已执行数据源：elasticsearch"));
        assertTrue(prompt.getValue().contains("剩余可选数据源：custom-diagnosis"));
    }

    private OpsMainAgentPlanningProtocolService service(OpsAgentLlmClient llmClient) {
        OpsMainAgentDeterministicPlanningService deterministic =
                new OpsMainAgentDeterministicPlanningService();
        return new OpsMainAgentPlanningProtocolService(llmClient, deterministic);
    }

    private OpsMainAgentPlanningProtocolService.ReplanInput replanInput(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            boolean graphScoped,
            String graphRoutingChoices,
            Set<String> graphRouteSources) {
        return new OpsMainAgentPlanningProtocolService.ReplanInput(
                request("继续排查"),
                OpsQuestionContext.from("继续排查"),
                fallback(),
                results,
                fallback(),
                1,
                2,
                graphScoped,
                graphRoutingChoices,
                graphRouteSources,
                sources(),
                List.of());
    }

    private OpsAgentRunRequestDTO request(String question) {
        return OpsAgentRunRequestDTO.builder()
                .question(question)
                .rangeMinutes(30)
                .promWindow("5m")
                .includeRecentLogs(false)
                .maxRounds(2)
                .subAgentMaxIterations(2)
                .build();
    }

    private OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallback() {
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent("METRIC_FIRST_INVESTIGATION")
                .reason("fallback")
                .tasks(List.of(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_PROM)
                        .agent("prometheus-agent")
                        .goal("检查指标")
                        .reason("兜底")
                        .priority(1)
                        .build()))
                .conditionalTasks(List.of())
                .skippedTasks(List.of())
                .build();
    }

    private Set<String> sources() {
        return new LinkedHashSet<>(List.of(
                OpsMainAgentPlanner.SOURCE_RAG,
                OpsMainAgentPlanner.SOURCE_ES,
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL));
    }
}
