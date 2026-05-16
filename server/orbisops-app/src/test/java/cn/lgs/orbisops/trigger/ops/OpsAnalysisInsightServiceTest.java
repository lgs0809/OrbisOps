package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsAnalysisInsightServiceTest {

    private final OpsAnalysisInsightService service =
            new OpsAnalysisInsightService();

    @Test
    void recognizesRealtimeDatasourceAliasesAndIgnoresBlockedOrErrorResults() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .prometheusStatus(status(null, "未选择"))
                .elasticsearchStatus(status(null, "未选择"))
                .mysqlSlowSqlStatus(status(null, "未选择"))
                .investigationResults(List.of(
                        result(" prometheus ", " BLOCKED "),
                        result("slow-sql", "ERROR"),
                        result(" es ", " FOUND ")))
                .build();

        List<OpsAnalysisResponseDTO.InsightDTO> insights = service.build(response);

        assertEquals(1, insights.size());
        assertEquals("INFO", insights.get(0).getLevel());
        assertEquals("实时数据已由子 Agent 查询", insights.get(0).getTitle());
        assertEquals(
                "当前运行已产生实时数据源 observation，但没有生成旧版结构化指标摘要。",
                insights.get(0).getDetail());
    }

    @Test
    void mapsUnavailableStatusMessageWithoutDefensiveNormalization() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .prometheusStatus(status(false, null))
                .elasticsearchStatus(status(null, "未选择"))
                .mysqlSlowSqlStatus(status(null, "未选择"))
                .build();

        List<OpsAnalysisResponseDTO.InsightDTO> insights = service.build(response);

        assertEquals(1, insights.size());
        assertEquals("WARN", insights.get(0).getLevel());
        assertEquals("Prometheus 数据不可用", insights.get(0).getTitle());
        assertEquals(null, insights.get(0).getDetail());
        assertEquals(
                "检查 Prometheus 容器、9090 端口和 scrape target。",
                insights.get(0).getSuggestion());
    }

    @Test
    void mapsStructuredSummaryToDomainPolicyAndBackToApiDto() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .rangeMinutes(15)
                .prometheusStatus(status(true, "ok"))
                .elasticsearchStatus(status(true, "ok"))
                .mysqlSlowSqlStatus(status(true, "ok"))
                .metricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                        .instanceTotal(2)
                        .instanceUp(1)
                        .totalQps(4D)
                        .errorRate(7D)
                        .heapMemoryUsagePercent(20D)
                        .build())
                .logSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                        .totalLogs(10L)
                        .errorLogs(1L)
                        .build())
                .slowSqlSummary(OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder()
                        .slowStatements(1L)
                        .maxQueryTimeMs(2_000D)
                        .build())
                .build();

        List<OpsAnalysisResponseDTO.InsightDTO> insights = service.build(response);

        assertEquals(List.of(
                        "业务实例存在下线",
                        "接口错误率偏高",
                        "最近日志存在 ERROR",
                        "发现 MySQL 慢 SQL"),
                insights.stream()
                        .map(OpsAnalysisResponseDTO.InsightDTO::getTitle)
                        .toList());
        assertEquals("HIGH", insights.get(0).getLevel());
        assertEquals("HIGH", insights.get(1).getLevel());
        assertEquals("WARN", insights.get(2).getLevel());
        assertEquals("WARN", insights.get(3).getLevel());
        assertEquals("最近 15 分钟 ERROR 日志 1 条。",
                insights.get(2).getDetail());
    }

    private OpsAnalysisResponseDTO.DataSourceStatusDTO status(
            Boolean available,
            String message) {
        return OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .available(available)
                .message(message)
                .build();
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO result(
            String source,
            String status) {
        return OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                .source(source)
                .status(status)
                .build();
    }
}
