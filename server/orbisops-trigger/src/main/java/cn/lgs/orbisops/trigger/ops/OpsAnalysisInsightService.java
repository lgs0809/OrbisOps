package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationInsight;
import cn.lgs.orbisops.domain.investigation.service.InvestigationInsightPolicy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** API DTO anti-corruption layer for deterministic investigation insights. */
final class OpsAnalysisInsightService {

    private static final InvestigationInsightPolicy POLICY =
            new InvestigationInsightPolicy();

    List<OpsAnalysisResponseDTO.InsightDTO> build(
            OpsAnalysisResponseDTO response) {
        OpsAnalysisResponseDTO.MetricSummaryDTO metrics = metricSummary(response);
        OpsAnalysisResponseDTO.LogSummaryDTO logs = logSummary(response);
        OpsAnalysisResponseDTO.SlowSqlSummaryDTO slowSql = slowSqlSummary(response);
        return POLICY.assess(new InvestigationInsightPolicy.Input(
                        source(response.getPrometheusStatus()),
                        source(response.getElasticsearchStatus()),
                        source(response.getMysqlSlowSqlStatus()),
                        new InvestigationInsightPolicy.Metrics(
                                metrics.getInstanceTotal(),
                                metrics.getInstanceUp(),
                                metrics.getTotalQps(),
                                metrics.getErrorRate(),
                                metrics.getHeapMemoryUsagePercent()),
                        new InvestigationInsightPolicy.Logs(
                                logs.getTotalLogs(),
                                logs.getErrorLogs()),
                        new InvestigationInsightPolicy.SlowSql(
                                slowSql.getSlowStatements(),
                                slowSql.getMaxQueryTimeMs()),
                        response.getRangeMinutes(),
                        realtimeResultQueried(response)))
                .stream()
                .map(this::project)
                .toList();
    }

    private InvestigationInsightPolicy.Source source(
            OpsAnalysisResponseDTO.DataSourceStatusDTO status) {
        return new InvestigationInsightPolicy.Source(
                status != null && Boolean.TRUE.equals(status.getAvailable()),
                status != null && Boolean.FALSE.equals(status.getAvailable()),
                status == null ? null : status.getMessage());
    }

    private boolean realtimeResultQueried(OpsAnalysisResponseDTO response) {
        return hasExecutedResult(response, "prometheus")
                || hasExecutedResult(response, "elasticsearch")
                || hasExecutedResult(response, "mysql_slow_sql");
    }

    private boolean hasExecutedResult(
            OpsAnalysisResponseDTO response,
            String source) {
        return Optional.ofNullable(response.getInvestigationResults())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(result -> source.equalsIgnoreCase(
                        normalizeSource(result.getSource())))
                .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getStatus)
                .filter(Objects::nonNull)
                .map(status -> status.trim().toUpperCase())
                .anyMatch(status -> !Set.of("BLOCKED", "ERROR").contains(status));
    }

    private String normalizeSource(String source) {
        if (source == null) {
            return "";
        }
        String normalized = source.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case "es" -> "elasticsearch";
            case "mysql", "mysql_slow", "mysql_slow_sql", "slow_sql" ->
                    "mysql_slow_sql";
            default -> normalized;
        };
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
        return OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                .totalLogs(0L)
                .errorLogs(0L)
                .warnLogs(0L)
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

    private OpsAnalysisResponseDTO.InsightDTO project(
            InvestigationInsight insight) {
        return OpsAnalysisResponseDTO.InsightDTO.builder()
                .level(insight.level())
                .title(insight.title())
                .detail(insight.detail())
                .suggestion(insight.suggestion())
                .build();
    }
}
