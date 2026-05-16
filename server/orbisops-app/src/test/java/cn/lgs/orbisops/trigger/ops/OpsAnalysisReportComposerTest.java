package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAnalysisReportComposerTest {

    @Test
    void shouldNotAppendLegacySkillContextToReportSnapshotOrPrompt() {
        OpsFinalReportService finalReportService = mock(OpsFinalReportService.class);
        when(finalReportService.buildFinalReport(any(), anyString())).thenReturn("# 运维分析结果\n\n## 结论\n暂无异常。");
        OpsAnalysisReportComposer composer = new OpsAnalysisReportComposer(finalReportService);
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("ops_skill_context_cleanup")
                .agentDefinitionId("demo-ops-agent")
                .agentRuntime("GRAPH")
                .rangeMinutes(30)
                .promWindow("5m")
                .generatedAt("2026-05-28 12:00:00")
                .elasticsearchStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder().available(false).message("未选择").build())
                .prometheusStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder().available(false).message("未选择").build())
                .mysqlSlowSqlStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder().available(false).message("未选择").build())
                .metricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder().build())
                .logSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder().build())
                .slowSqlSummary(OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder().build())
                .endpointMetrics(new ArrayList<>())
                .recentLogs(new ArrayList<>())
                .slowSqlSamples(new ArrayList<>())
                .executionNotes(new ArrayList<>())
                .build();

        String snapshot = composer.buildDataSnapshot(response);
        composer.composeFinalReport(response);

        assertFalse(snapshot.contains("已加载运维 Skill"));
        assertFalse(response.getAiPrompt().contains("运维 Skill 规则"));
        assertFalse(response.getAiPrompt().contains("demo-ops:"));
    }

    @Test
    void shouldRecognizeRealtimeEvidenceFromGenericReactNodes() {
        OpsFinalReportService finalReportService = mock(OpsFinalReportService.class);
        OpsAnalysisReportComposer composer = new OpsAnalysisReportComposer(finalReportService);
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .prometheusStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                        .available(null)
                        .message("未选择")
                        .build())
                .metricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder().build())
                .logSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder().build())
                .slowSqlSummary(OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder().build())
                .investigationResults(List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .agent("prometheus-agent")
                        .status("FOUND")
                        .summary("已执行 PromQL 并返回 up 指标")
                        .build()))
                .build();

        List<OpsAnalysisResponseDTO.InsightDTO> insights = composer.buildInsights(response);

        assertTrue(insights.stream().anyMatch(item -> "实时数据已由子 Agent 查询".equals(item.getTitle())));
        assertFalse(insights.stream().anyMatch(item -> "本轮未查询实时运行数据".equals(item.getTitle())));
    }
}
