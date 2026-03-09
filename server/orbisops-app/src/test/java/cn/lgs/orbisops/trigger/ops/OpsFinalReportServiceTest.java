package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpsFinalReportServiceTest {

    @Test
    public void shouldAcceptLlmReportWhenEvidenceBoundaryMatchesExecutedSources() {
        String markdown = """
                # 示例平台运维分析结果

                ## 结论
                日志侧发现锁单失败集中出现，需要继续观察下单链路。

                ## 关键证据
                - 日志显示 `ERR_LOCK_001` 在窗口内集中出现。

                ## 风险与可能原因
                - 可能存在活动库存竞争或锁单参数异常。

                ## 建议动作
                - 复核活动库存和锁单链路入参。

                ## 数据源与缺口
                - Elasticsearch 已查询；缺口：暂未查询 Prometheus 指标。
                """;
        OpsFinalReportService service = serviceWithLlmReport(markdown);
        OpsAnalysisResponseDTO response = responseWithLogEvidence();

        String report = service.buildFinalReport(response, "日志证据快照");

        assertEquals(markdown.trim(), report);
        assertTrue(response.getExecutionNotes().contains("最终报告由 LLM 基于真实证据合成。"));
    }

    @Test
    public void shouldFallbackWhenLlmClaimsUnexecutedDatasourceEvidence() {
        String markdown = """
                # 示例平台运维分析结果

                ## 结论
                接口延迟升高。

                ## 关键证据
                - Prometheus 查询结果显示 CPU 使用率升高。

                ## 风险与可能原因
                - 资源瓶颈。

                ## 建议动作
                - 扩容实例。

                ## 数据源与缺口
                - Elasticsearch 已查询；缺口：Prometheus 未查询。
                """;
        OpsFinalReportService service = serviceWithLlmReport(markdown);
        OpsAnalysisResponseDTO response = responseWithLogEvidence();

        String report = service.buildFinalReport(response, "日志证据快照");

        assertTrue(report.contains("## 证据快照"));
        assertTrue(response.getExecutionNotes().contains("LLM 最终报告未通过证据边界检查，已降级到规则报告。"));
    }

    @Test
    public void shouldFallbackStrictFinalReportWhenLlmClaimsUnexecutedDatasourceEvidence() {
        String markdown = """
                # 示例平台运维分析结果

                ## 结论
                接口延迟升高。

                ## 关键证据
                - Prometheus 查询结果显示 CPU 使用率升高。

                ## 风险与可能原因
                - 资源瓶颈。

                ## 建议动作
                - 扩容实例。

                ## 数据源与缺口
                - Elasticsearch 已查询；缺口：Prometheus 未查询。
                """;
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        JSONObject json = new JSONObject();
        json.put("markdownReport", markdown);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection())).thenReturn(json);
        doThrow(new OpsLlmDegradationException("ops-final-report", "最终报告未通过证据边界检查"))
                .when(llmClient).rejectDegradation(eq("ops-final-report"), contains("证据边界"));
        OpsFinalReportService service = new OpsFinalReportService(llmClient);

        OpsAnalysisResponseDTO response = responseWithLogEvidence();
        String report = service.buildFinalReport(response, "日志证据快照");

        assertTrue(report.contains("## 证据快照"));
        assertTrue(response.getExecutionNotes().stream().anyMatch(note -> note.contains("LLM 最终报告不可用")));
        assertTrue(response.getExecutionNotes().contains("LLM 最终报告未通过证据边界检查，已降级到规则报告。"));
    }

    private OpsFinalReportService serviceWithLlmReport(String markdown) {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.available()).thenReturn(true);
        JSONObject json = new JSONObject();
        json.put("markdownReport", markdown);
        when(llmClient.chatJsonObject(anyString(), anyString(), anyString(), anyCollection())).thenReturn(json);
        OpsFinalReportService service = new OpsFinalReportService(llmClient);
        return service;
    }

    private OpsAnalysisResponseDTO responseWithLogEvidence() {
        return OpsAnalysisResponseDTO.builder()
                .analysisId("ops_report_boundary")
                .rangeMinutes(15)
                .promWindow("5m")
                .generatedAt("2026-05-26 13:40:00")
                .logSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                        .totalLogs(12L)
                        .errorLogs(3L)
                        .build())
                .investigationResults(List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .agent("elk-agent")
                        .status("FOUND")
                        .summary("发现锁单错误日志")
                        .evidence(List.of("ERR_LOCK_001"))
                        .gaps(List.of())
                        .build()))
                .executionNotes(new ArrayList<>())
                .build();
    }
}
