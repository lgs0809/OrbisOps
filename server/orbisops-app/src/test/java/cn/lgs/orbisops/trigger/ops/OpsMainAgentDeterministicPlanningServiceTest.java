package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainAgentDeterministicPlanningServiceTest {

    private final OpsMainAgentDeterministicPlanningService service =
            new OpsMainAgentDeterministicPlanningService();

    @Test
    void mapsQuestionContextToLogFirstPlanAndFiltersUnavailableSources() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .question("检查 traceId trace-123 的 ERROR 日志")
                .includeRecentLogs(false)
                .build();
        OpsQuestionContext context = OpsQuestionContext.from(request.getQuestion());

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = service.initial(
                request,
                context,
                new LinkedHashSet<>(List.of(
                        OpsMainAgentPlanner.SOURCE_ES,
                        OpsMainAgentPlanner.SOURCE_RAG)));

        assertEquals("LOG_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_ES), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
        assertTrue(sources(plan.getSkippedTasks()).stream()
                .noneMatch(OpsMainAgentPlanner.SOURCE_PROM::equals));
    }

    @Test
    void mapsObservationDtosIntoDeterministicReplan() {
        OpsQuestionContext context = OpsQuestionContext.from("检查错误日志");
        OpsAnalysisResponseDTO.InvestigationResultDTO result =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_ES)
                        .status("INSUFFICIENT")
                        .summary("日志证据不足")
                        .evidence(List.of())
                        .gaps(List.of("缺少指标"))
                        .suggestedAdjustments(List.of("查询 prometheus"))
                        .build();

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = service.replan(
                context,
                List.of(result),
                1,
                2,
                sources());

        assertEquals("REPLAN_CONTINUE", plan.getIntent());
        assertEquals(List.of(
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
    }

    @Test
    void guardrailDtoMappingPreservesNullablePriorityAndChangeIntent() {
        OpsAnalysisResponseDTO.InvestigationTaskDTO ragTask =
                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_RAG)
                        .agent("rag-knowledge-agent")
                        .goal("查知识")
                        .reason("知识解释")
                        .priority(null)
                        .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallback =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("KNOWLEDGE_FIRST_INVESTIGATION")
                        .reason("fallback")
                        .changeRequested(false)
                        .changeIntent("")
                        .tasks(List.of(ragTask))
                        .conditionalTasks(List.of())
                        .skippedTasks(List.of())
                        .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO llm =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("INCIDENT_INVESTIGATION")
                        .reason("llm")
                        .changeRequested(true)
                        .changeIntent("更新告警模板")
                        .tasks(List.of(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                .source(OpsMainAgentPlanner.SOURCE_PROM)
                                .agent("prometheus-agent")
                                .build()))
                        .conditionalTasks(List.of())
                        .skippedTasks(List.of())
                        .build();

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO guarded = service.guardrails(
                llm,
                fallback,
                OpsQuestionContext.from("告警模板应该包含什么，如何解释"));

        assertEquals(OpsMainAgentPlanner.SOURCE_RAG, guarded.getTasks().get(0).getSource());
        assertNull(guarded.getTasks().get(0).getPriority());
        assertEquals(Boolean.TRUE, guarded.getChangeRequested());
        assertEquals("更新告警模板", guarded.getChangeIntent());
    }

    @Test
    void allSourcesStopKeepsLegacyNullableChangeFields() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO stop =
                service.allSourcesReplanStop(null);

        assertEquals("STOP", stop.getIntent());
        assertNull(stop.getChangeRequested());
        assertNull(stop.getChangeIntent());
        assertTrue(stop.getTasks().isEmpty());
    }

    private Set<String> sources() {
        return new LinkedHashSet<>(List.of(
                OpsMainAgentPlanner.SOURCE_RAG,
                OpsMainAgentPlanner.SOURCE_ES,
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL));
    }

    private List<String> sources(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return tasks.stream().map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource).toList();
    }
}
