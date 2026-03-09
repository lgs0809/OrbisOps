package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsInvestigationFollowUpServiceTest {

    private final OpsInvestigationFollowUpService service =
            new OpsInvestigationFollowUpService();

    @Test
    void mapsDtoObservationAndRuleDecisionBackToDto() {
        OpsAnalysisResponseDTO.InvestigationResultDTO latest =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .status("INSUFFICIENT")
                        .summary("数据库连接池耗时")
                        .evidence(List.of("sql timeout"))
                        .gaps(List.of("缺少指标"))
                        .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("RUNTIME_INVESTIGATION")
                        .conditionalTasks(List.of())
                        .build();

        OpsInvestigationFollowUpService.Decision decision = service.decide(
                plan,
                List.of(latest),
                latest,
                Set.of("elasticsearch"),
                Set.of(),
                OpsQuestionContext.from("排查数据库慢 SQL 并解释原因"),
                true);

        assertEquals(
                List.of("prometheus", "mysql_slow_sql", "rag"),
                decision.tasks().stream()
                        .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                        .toList());
        assertEquals("prometheus-agent", decision.tasks().get(0).getAgent());
        assertEquals(2, decision.tasks().get(0).getPriority());
        assertEquals(3, decision.notes().size());
    }

    @Test
    void preservesNullablePriorityOnConditionalTask() {
        OpsAnalysisResponseDTO.InvestigationTaskDTO conditional =
                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                        .source("elasticsearch")
                        .agent("es-log-agent")
                        .goal("查日志")
                        .reason("指标异常")
                        .priority(null)
                        .condition("prom anomaly")
                        .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO prom =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .status("FOUND")
                        .summary("错误率异常")
                        .evidence(List.of("5xx=10%"))
                        .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("RUNTIME_INVESTIGATION")
                        .conditionalTasks(List.of(conditional))
                        .build();

        OpsInvestigationFollowUpService.Decision decision = service.decide(
                plan,
                List.of(prom),
                prom,
                Set.of("prometheus"),
                Set.of(),
                OpsQuestionContext.from("检查当前错误率"),
                true);

        assertEquals(1, decision.tasks().size());
        assertNull(decision.tasks().get(0).getPriority());
    }

    @Test
    void knowledgeStopUsesLegacyRuntimeFilterBoundary() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("KNOWLEDGE_FIRST_INVESTIGATION")
                        .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO rag =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("rag")
                        .status("FOUND")
                        .summary("已找到 SOP")
                        .build();

        assertTrue(service.shouldStopAfterKnowledgeObservation(
                plan,
                rag,
                OpsQuestionContext.from("如何解释只读安全边界")));
        assertFalse(service.shouldStopAfterKnowledgeObservation(
                plan,
                rag,
                OpsQuestionContext.from("如何解释 traceId trace-123456 的错误")));
    }
}
