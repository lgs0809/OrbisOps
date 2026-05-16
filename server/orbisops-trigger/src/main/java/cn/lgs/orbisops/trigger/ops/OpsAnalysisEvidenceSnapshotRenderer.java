package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Markdown evidence snapshot rendering boundary for operations analysis. */
final class OpsAnalysisEvidenceSnapshotRenderer {

    String render(OpsAnalysisResponseDTO response) {
        OpsAnalysisResponseDTO.MetricSummaryDTO metric = metricSummary(response);
        OpsAnalysisResponseDTO.LogSummaryDTO logSummary = logSummary(response);
        OpsAnalysisResponseDTO.SlowSqlSummaryDTO slowSqlSummary = slowSqlSummary(response);
        boolean prometheusQueried = sourceQueried(response.getPrometheusStatus());
        boolean elasticsearchQueried = sourceQueried(response.getElasticsearchStatus());
        boolean mysqlSlowSqlQueried = sourceQueried(response.getMysqlSlowSqlStatus());
        StringBuilder report = new StringBuilder();
        report.append("## 真实数据快照\n\n");
        report.append("- 生成时间：").append(response.getGeneratedAt()).append('\n');
        report.append("- 日志窗口：最近 ").append(response.getRangeMinutes()).append(" 分钟\n");
        report.append("- Prometheus rate 窗口：").append(response.getPromWindow()).append("\n");
        report.append("- Elasticsearch：").append(sourceText(response.getElasticsearchStatus())).append('\n');
        report.append("- Prometheus：").append(sourceText(response.getPrometheusStatus())).append("\n");
        report.append("- MySQL 慢 SQL：").append(sourceText(response.getMysqlSlowSqlStatus())).append('\n');
        report.append("- Agent 定义：").append(value(response.getAgentDefinitionId())).append('\n');
        report.append("- Agent 运行时：").append(value(response.getAgentRuntime())).append("\n\n");

        appendInvestigationPlan(report, response);
        appendExecutionNotes(report, response);
        appendAgentExecutionSteps(report, response);
        appendMetrics(report, response, metric, prometheusQueried);
        appendLogs(report, logSummary, elasticsearchQueried);
        appendSlowSql(report, response, slowSqlSummary, mysqlSlowSqlQueried);
        appendInvestigationResults(report, response);
        appendInsights(report, response);
        return report.toString();
    }

    private void appendInvestigationPlan(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        if (response.getInvestigationPlan() == null) {
            return;
        }
        report.append("### 主 Agent 调查计划\n\n");
        report.append("- 意图：").append(response.getInvestigationPlan().getIntent()).append('\n');
        report.append("- 路由理由：").append(response.getInvestigationPlan().getReason()).append("\n\n");
        report.append("- 用户是否明确要求变更：")
                .append(Boolean.TRUE.equals(response.getInvestigationPlan().getChangeRequested()) ? "是" : "否")
                .append('\n');
        if (Boolean.TRUE.equals(response.getInvestigationPlan().getChangeRequested())) {
            report.append("- 变更意图：")
                    .append(value(response.getInvestigationPlan().getChangeIntent()))
                    .append("\n\n");
        }
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks = Optional.ofNullable(
                response.getInvestigationPlan().getTasks()).orElse(List.of());
        report.append("已选择数据源：");
        report.append(tasks.isEmpty()
                ? "无"
                : tasks.stream()
                        .map(task -> task.getSource() + "(" + task.getReason() + ")")
                        .collect(Collectors.joining("；")));
        report.append("\n\n");
    }

    private void appendExecutionNotes(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        if (response.getExecutionNotes() == null
                || response.getExecutionNotes().isEmpty()) {
            return;
        }
        report.append("### 主 Agent 执行复盘\n\n");
        for (String note : response.getExecutionNotes()) {
            report.append("- ").append(note).append('\n');
        }
        report.append('\n');
    }

    private void appendAgentExecutionSteps(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        if (response.getAgentExecutionSteps() == null
                || response.getAgentExecutionSteps().isEmpty()) {
            return;
        }
        report.append("### Agent Graph 执行节点\n\n");
        for (OpsAnalysisResponseDTO.AgentExecutionStepDTO step
                : response.getAgentExecutionSteps()) {
            report.append("- ").append(step.getNodeId()).append(" / ")
                    .append(step.getNodeType()).append(" / ")
                    .append(step.getAgent()).append("：")
                    .append(step.getStatus()).append("，")
                    .append(step.getSummary()).append('\n');
        }
        report.append('\n');
    }

    private void appendMetrics(
            StringBuilder report,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.MetricSummaryDTO metric,
            boolean queried) {
        report.append("### 监控摘要\n\n");
        if (!queried) {
            report.append("- Prometheus 本轮未查询，未生成实例、QPS、错误率、延迟或资源指标。\n\n");
            return;
        }
        report.append("- 实例状态：").append(value(metric.getInstanceUp()))
                .append('/').append(value(metric.getInstanceTotal())).append(" UP\n");
        report.append("- 总 QPS：").append(value(metric.getTotalQps())).append('\n');
        report.append("- 5xx 错误率：").append(value(metric.getErrorRate())).append("%\n");
        report.append("- Heap 使用率：").append(value(metric.getHeapMemoryUsagePercent())).append("%\n");
        report.append("- 进程 CPU 使用率：").append(value(metric.getProcessCpuUsagePercent())).append("%\n\n");
        report.append("### 接口指标 Top\n\n");
        report.append("| URI | Method | Status | QPS | 平均响应(ms) |\n");
        report.append("| --- | --- | --- | ---: | ---: |\n");
        endpointMetrics(response).stream().limit(8).forEach(endpoint -> report
                .append("| ").append(markdownCell(endpoint.getUri()))
                .append(" | ").append(markdownCell(endpoint.getMethod()))
                .append(" | ").append(markdownCell(endpoint.getStatus()))
                .append(" | ").append(value(endpoint.getQps()))
                .append(" | ").append(value(endpoint.getAvgResponseMs()))
                .append(" |\n"));
        if (endpointMetrics(response).isEmpty()) {
            report.append("| 暂无接口流量 | - | - | 0 | 0 |\n");
        }
        report.append('\n');
    }

    private void appendLogs(
            StringBuilder report,
            OpsAnalysisResponseDTO.LogSummaryDTO logSummary,
            boolean queried) {
        report.append("### 日志摘要\n\n");
        if (!queried) {
            report.append("- Elasticsearch 本轮未查询，未生成日志总量、ERROR 或 WARN 统计。\n\n");
            return;
        }
        report.append("- 日志总量：").append(value(logSummary.getTotalLogs())).append('\n');
        report.append("- ERROR：").append(value(logSummary.getErrorLogs())).append('\n');
        report.append("- WARN：").append(value(logSummary.getWarnLogs())).append("\n\n");
    }

    private void appendSlowSql(
            StringBuilder report,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.SlowSqlSummaryDTO summary,
            boolean queried) {
        report.append("### MySQL 慢 SQL 摘要\n\n");
        if (!queried) {
            report.append("- MySQL 慢 SQL 本轮未查询，未生成慢 SQL 命中数或耗时统计。\n");
            return;
        }
        report.append("- 命中慢 SQL：").append(value(summary.getSlowStatements())).append('\n');
        report.append("- 平均耗时(ms)：").append(value(summary.getAvgQueryTimeMs())).append('\n');
        report.append("- 最高耗时(ms)：").append(value(summary.getMaxQueryTimeMs())).append('\n');
        report.append("- 扫描行数合计：").append(value(summary.getRowsExamined())).append("\n\n");
        report.append("### MySQL 慢 SQL Top\n\n");
        report.append("| SQL | 耗时(ms) | 扫描行数 | 执行次数 |\n");
        report.append("| --- | ---: | ---: | ---: |\n");
        slowSqlSamples(response).stream().limit(8).forEach(sample -> report
                .append("| ").append(markdownCell(abbreviate(sample.getSqlText(), 160)))
                .append(" | ").append(value(sample.getQueryTimeMs()))
                .append(" | ").append(value(sample.getRowsExamined()))
                .append(" | ").append(value(sample.getCountStar()))
                .append(" |\n"));
        if (slowSqlSamples(response).isEmpty()) {
            report.append("| 暂无慢 SQL | 0 | 0 | 0 |\n");
        }
        report.append('\n');
    }

    private void appendInvestigationResults(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        report.append("\n### 子 Agent 调查结果\n\n");
        if (response.getInvestigationResults() == null
                || response.getInvestigationResults().isEmpty()) {
            report.append("- 暂无子 Agent 调查结果。\n");
            return;
        }
        for (OpsAnalysisResponseDTO.InvestigationResultDTO result
                : response.getInvestigationResults()) {
            report.append("- ").append(result.getAgent()).append(" / ")
                    .append(result.getSource()).append("：")
                    .append(result.getStatus()).append("，")
                    .append(result.getSummary()).append("，confidence=")
                    .append(value(result.getConfidence())).append('\n');
            Optional.ofNullable(result.getEvidence()).orElse(List.of()).stream().limit(3)
                    .forEach(evidence -> report.append("  - 证据：").append(evidence).append('\n'));
            Optional.ofNullable(result.getGaps()).orElse(List.of()).stream().limit(2)
                    .forEach(gap -> report.append("  - 缺口：").append(gap).append('\n'));
            Optional.ofNullable(result.getSuggestedAdjustments()).orElse(List.of()).stream().limit(2)
                    .forEach(adjustment -> report.append("  - 建议调整：").append(adjustment).append('\n'));
        }
    }

    private void appendInsights(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        report.append("\n### 系统规则提示\n\n");
        List<OpsAnalysisResponseDTO.InsightDTO> insights = Optional.ofNullable(
                response.getInsights()).orElse(List.of());
        for (int i = 0; i < insights.size(); i++) {
            OpsAnalysisResponseDTO.InsightDTO insight = insights.get(i);
            report.append(i + 1).append(". ").append(insight.getTitle()).append("：")
                    .append(insight.getDetail()).append(" 建议：")
                    .append(insight.getSuggestion()).append('\n');
        }
    }

    private OpsAnalysisResponseDTO.MetricSummaryDTO metricSummary(
            OpsAnalysisResponseDTO response) {
        if (response.getMetricSummary() != null) {
            return response.getMetricSummary();
        }
        return OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                .instanceTotal(0)
                .instanceUp(0)
                .totalQps(0D)
                .errorQps(0D)
                .errorRate(0D)
                .heapMemoryUsagePercent(0D)
                .processCpuUsagePercent(0D)
                .build();
    }

    private OpsAnalysisResponseDTO.LogSummaryDTO logSummary(
            OpsAnalysisResponseDTO response) {
        if (response.getLogSummary() != null) {
            return response.getLogSummary();
        }
        Map<String, Long> levels = new LinkedHashMap<>();
        levels.put("ERROR", 0L);
        levels.put("WARN", 0L);
        levels.put("INFO", 0L);
        return OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                .totalLogs(0L)
                .errorLogs(0L)
                .warnLogs(0L)
                .levelCounts(levels)
                .topLoggers(new ArrayList<>())
                .build();
    }

    private OpsAnalysisResponseDTO.SlowSqlSummaryDTO slowSqlSummary(
            OpsAnalysisResponseDTO response) {
        if (response.getSlowSqlSummary() != null) {
            return response.getSlowSqlSummary();
        }
        return OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder()
                .totalStatements(0L)
                .slowStatements(0L)
                .avgQueryTimeMs(0D)
                .maxQueryTimeMs(0D)
                .rowsExamined(0L)
                .build();
    }

    private List<OpsAnalysisResponseDTO.EndpointMetricDTO> endpointMetrics(
            OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getEndpointMetrics()).orElse(List.of());
    }

    private List<OpsAnalysisResponseDTO.SlowSqlSampleDTO> slowSqlSamples(
            OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getSlowSqlSamples()).orElse(List.of());
    }

    private String sourceText(OpsAnalysisResponseDTO.DataSourceStatusDTO status) {
        if (status == null) {
            return "未初始化";
        }
        if (status.getAvailable() == null) {
            return status.getMessage();
        }
        return Boolean.TRUE.equals(status.getAvailable())
                ? "可用，" + status.getMessage()
                : "不可用，" + status.getMessage();
    }

    private boolean sourceQueried(
            OpsAnalysisResponseDTO.DataSourceStatusDTO status) {
        return status != null && Boolean.TRUE.equals(status.getAvailable());
    }

    private String value(Object value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String markdownCell(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
