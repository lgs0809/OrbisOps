package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisEvidenceSnapshotRendererTest {

    private final OpsAnalysisEvidenceSnapshotRenderer renderer =
            new OpsAnalysisEvidenceSnapshotRenderer();

    @Test
    void rendersAllEvidenceSectionsInOriginalOrderAndLimitsDetailRows() {
        List<OpsAnalysisResponseDTO.EndpointMetricDTO> endpoints = new ArrayList<>();
        List<OpsAnalysisResponseDTO.SlowSqlSampleDTO> slowSqlSamples = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            endpoints.add(OpsAnalysisResponseDTO.EndpointMetricDTO.builder()
                    .uri(index == 0 ? "/api|orders" : "/api/" + index)
                    .method("GET")
                    .status(index == 0 ? "500" : "200")
                    .qps(10D - index)
                    .avgResponseMs(100D + index)
                    .build());
            slowSqlSamples.add(OpsAnalysisResponseDTO.SlowSqlSampleDTO.builder()
                    .sqlText(index == 0
                            ? "select|" + "x".repeat(180)
                            : "sql-" + index)
                    .queryTimeMs(500D + index)
                    .rowsExamined(1_000L + index)
                    .countStar((long) index + 1)
                    .build());
        }
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .generatedAt("2026-07-27 10:00:00")
                .rangeMinutes(30)
                .promWindow("5m")
                .agentDefinitionId("ops-agent")
                .agentRuntime("GRAPH")
                .elasticsearchStatus(status(true, "logs ok"))
                .prometheusStatus(status(true, "metrics ok"))
                .mysqlSlowSqlStatus(status(true, "slow sql ok"))
                .investigationPlan(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("root cause")
                        .reason("multi source")
                        .changeRequested(true)
                        .changeIntent("repair payment")
                        .tasks(List.of(
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source("prometheus")
                                        .reason("metrics")
                                        .build(),
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source("elasticsearch")
                                        .reason("logs")
                                        .build()))
                        .build())
                .executionNotes(List.of("first note", "second note"))
                .agentExecutionSteps(List.of(
                        OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder()
                                .nodeId("node-1")
                                .nodeType("SUB_AGENT")
                                .agent("prometheus-agent")
                                .status("SUCCEEDED")
                                .summary("metrics collected")
                                .build()))
                .metricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                        .instanceUp(2)
                        .instanceTotal(3)
                        .totalQps(12.5D)
                        .errorRate(4.2D)
                        .heapMemoryUsagePercent(70D)
                        .processCpuUsagePercent(40D)
                        .build())
                .endpointMetrics(endpoints)
                .logSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                        .totalLogs(100L)
                        .errorLogs(5L)
                        .warnLogs(10L)
                        .build())
                .slowSqlSummary(OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder()
                        .slowStatements(9L)
                        .avgQueryTimeMs(700D)
                        .maxQueryTimeMs(1_200D)
                        .rowsExamined(20_000L)
                        .build())
                .slowSqlSamples(slowSqlSamples)
                .investigationResults(List.of(
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .agent("mysql-agent")
                                .source("mysql")
                                .status("FOUND")
                                .summary("slow query found")
                                .confidence(0.82D)
                                .evidence(List.of("e1", "e2", "e3", "e4"))
                                .gaps(List.of("g1", "g2", "g3"))
                                .suggestedAdjustments(List.of("a1", "a2", "a3"))
                                .build()))
                .insights(List.of(
                        insight("risk", "detail-1", "suggestion-1"),
                        insight("capacity", "detail-2", "suggestion-2")))
                .build();

        String snapshot = renderer.render(response);

        assertTrue(snapshot.contains("- Elasticsearch：可用，logs ok"));
        assertTrue(snapshot.contains("- Prometheus：可用，metrics ok"));
        assertTrue(snapshot.contains("- MySQL 慢 SQL：可用，slow sql ok"));
        assertTrue(snapshot.contains("- 用户是否明确要求变更：是"));
        assertTrue(snapshot.contains("- 变更意图：repair payment"));
        assertTrue(snapshot.contains("prometheus(metrics)；elasticsearch(logs)"));
        assertTrue(snapshot.contains("- node-1 / SUB_AGENT / prometheus-agent：SUCCEEDED，metrics collected"));
        assertTrue(snapshot.contains("- 实例状态：2/3 UP"));
        assertTrue(snapshot.contains("| /api\\|orders | GET | 500 | 10.0 | 100.0 |"));
        assertFalse(snapshot.contains("| /api/8 |"));
        assertTrue(snapshot.contains("- 日志总量：100"));
        assertTrue(snapshot.contains("- 扫描行数合计：20000"));
        assertTrue(snapshot.contains("select\\|"));
        assertTrue(snapshot.contains("... | 500.0 | 1000 | 1 |"));
        assertFalse(snapshot.contains("| sql-8 |"));
        assertTrue(snapshot.contains("  - 证据：e3"));
        assertFalse(snapshot.contains("  - 证据：e4"));
        assertTrue(snapshot.contains("  - 缺口：g2"));
        assertFalse(snapshot.contains("  - 缺口：g3"));
        assertTrue(snapshot.contains("  - 建议调整：a2"));
        assertFalse(snapshot.contains("  - 建议调整：a3"));
        assertTrue(snapshot.contains("1. risk：detail-1 建议：suggestion-1"));
        assertTrue(snapshot.contains("2. capacity：detail-2 建议：suggestion-2"));

        assertOrdered(snapshot,
                "### 主 Agent 调查计划",
                "### 主 Agent 执行复盘",
                "### Agent Graph 执行节点",
                "### 监控摘要",
                "### 日志摘要",
                "### MySQL 慢 SQL 摘要",
                "### 子 Agent 调查结果",
                "### 系统规则提示");
    }

    @Test
    void rendersOriginalUnavailableAndNullDefaultsWithoutInventingEvidence() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .rangeMinutes(null)
                .promWindow(null)
                .elasticsearchStatus(null)
                .prometheusStatus(status(null, "未选择"))
                .mysqlSlowSqlStatus(status(false, "connection failed"))
                .investigationResults(List.of())
                .build();

        String snapshot = renderer.render(response);

        assertTrue(snapshot.contains("- Elasticsearch：未初始化"));
        assertTrue(snapshot.contains("- Prometheus：未选择"));
        assertTrue(snapshot.contains("- MySQL 慢 SQL：不可用，connection failed"));
        assertTrue(snapshot.contains("- Agent 定义：0"));
        assertTrue(snapshot.contains("- Agent 运行时：0"));
        assertTrue(snapshot.contains("Prometheus 本轮未查询"));
        assertTrue(snapshot.contains("Elasticsearch 本轮未查询"));
        assertTrue(snapshot.contains("MySQL 慢 SQL 本轮未查询"));
        assertTrue(snapshot.contains("- 暂无子 Agent 调查结果。"));
        assertFalse(snapshot.contains("### 接口指标 Top"));
        assertFalse(snapshot.contains("### MySQL 慢 SQL Top"));
    }

    private OpsAnalysisResponseDTO.DataSourceStatusDTO status(
            Boolean available,
            String message) {
        return OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .available(available)
                .message(message)
                .build();
    }

    private OpsAnalysisResponseDTO.InsightDTO insight(
            String title,
            String detail,
            String suggestion) {
        return OpsAnalysisResponseDTO.InsightDTO.builder()
                .title(title)
                .detail(detail)
                .suggestion(suggestion)
                .build();
    }

    private void assertOrdered(String value, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = value.indexOf(marker);
            assertTrue(current > previous, marker + " should follow previous section");
            previous = current;
        }
    }
}
