package cn.lgs.orbisops.trigger.application.audit;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.api.dto.OpsAuditRecordDTO;
import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisAuditMapperTest {

    private final OpsAnalysisAuditMapper mapper = new OpsAnalysisAuditMapper();

    @Test
    void mapsSuccessfulAnalysisIntoTypedAuditRecord() {
        AnalysisAuditRecord record = mapper.success(request(), response("analysis-1"), 120L);

        assertEquals("analysis-1", record.analysisId());
        assertTrue(record.success());
        assertEquals("LOG_FIRST_INVESTIGATION", record.intent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_ES, OpsMainAgentPlanner.SOURCE_PROM),
                record.selectedSources());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_ES), record.executedSources());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), record.skippedSources());
        assertEquals("FOUND", record.resultStatuses().get(OpsMainAgentPlanner.SOURCE_ES));
        assertEquals("SKIPPED", record.resultStatuses().get(OpsMainAgentPlanner.SOURCE_PROM));
        assertEquals(List.of("WARN"), record.insightLevels());
        assertEquals("最近日志存在 ERROR：ERROR 日志 1 条", record.conclusion());
    }

    @Test
    void mapsFailureAndLegacyDtoView() {
        AnalysisAuditRecord failure = mapper.failure(
                request(), new IllegalStateException("ES timeout"), 80L);
        OpsAuditRecordDTO view = mapper.view(failure);

        assertTrue(failure.analysisId().startsWith("ops_failed_"));
        assertFalse(failure.success());
        assertEquals(List.of("ERROR"), failure.insightLevels());
        assertEquals("ES timeout", failure.errorMessage());
        assertEquals(failure.analysisId(), view.getAnalysisId());
        assertEquals(false, view.getSuccess());
        assertEquals("ES timeout", view.getErrorMessage());
    }

    @Test
    void suppliesCompatibilityConclusionWhenInsightsAreMissing() {
        AnalysisAuditRecord record = mapper.success(
                request(), OpsAnalysisResponseDTO.builder()
                        .analysisId("analysis-2")
                        .rangeMinutes(15)
                        .promWindow("5m")
                        .generatedAt("now")
                        .build(), 10L);

        assertEquals("未生成系统规则结论", record.conclusion());
        assertTrue(record.selectedSources().isEmpty());
        assertTrue(record.resultStatuses().isEmpty());
    }

    private OpsAgentRunRequestDTO request() {
        return OpsAgentRunRequestDTO.builder()
                .question("traceId:abc123XYZ789 报错")
                .rangeMinutes(15)
                .promWindow("5m")
                .build();
    }

    private OpsAnalysisResponseDTO response(String analysisId) {
        return OpsAnalysisResponseDTO.builder()
                .analysisId(analysisId)
                .rangeMinutes(15)
                .promWindow("5m")
                .generatedAt("2026-05-08 10:00:00")
                .investigationPlan(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("LOG_FIRST_INVESTIGATION")
                        .tasks(List.of(
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source(OpsMainAgentPlanner.SOURCE_ES)
                                        .agent("es-log-agent")
                                        .build(),
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source(OpsMainAgentPlanner.SOURCE_PROM)
                                        .agent("prometheus-agent")
                                        .build()))
                        .skippedTasks(List.of(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                .source(OpsMainAgentPlanner.SOURCE_PROM)
                                .agent("prometheus-agent")
                                .build()))
                        .build())
                .investigationResults(List.of(
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .source(OpsMainAgentPlanner.SOURCE_ES)
                                .agent("es-log-agent")
                                .status("FOUND")
                                .build(),
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .source(OpsMainAgentPlanner.SOURCE_PROM)
                                .agent("prometheus-agent")
                                .status("SKIPPED")
                                .build()))
                .insights(List.of(OpsAnalysisResponseDTO.InsightDTO.builder()
                        .level("WARN")
                        .title("最近日志存在 ERROR")
                        .detail("ERROR 日志 1 条")
                        .build()))
                .build();
    }
}
