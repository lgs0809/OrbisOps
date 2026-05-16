package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsEsLogSettings;
import cn.lgs.orbisops.trigger.ops.OpsPrometheusSettings;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Creates the stable public response shell before runtime execution starts. */
final class OpsAnalysisResponseShellFactory {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OpsEsLogSettings esLogSettings;
    private final OpsPrometheusSettings prometheusSettings;
    private final Supplier<String> generatedAtSupplier;

    OpsAnalysisResponseShellFactory(
            OpsEsLogSettings esLogSettings,
            OpsPrometheusSettings prometheusSettings) {
        this(
                esLogSettings,
                prometheusSettings,
                () -> LocalDateTime.now().format(DATE_TIME_FORMATTER));
    }

    OpsAnalysisResponseShellFactory(
            OpsEsLogSettings esLogSettings,
            OpsPrometheusSettings prometheusSettings,
            Supplier<String> generatedAtSupplier) {
        this.esLogSettings = esLogSettings == null ? OpsEsLogSettings.defaults() : esLogSettings;
        this.prometheusSettings = prometheusSettings == null
                ? OpsPrometheusSettings.defaults()
                : prometheusSettings;
        this.generatedAtSupplier = generatedAtSupplier;
    }

    OpsAnalysisResponseDTO create(OpsAgentRunRequestDTO input) {
        OpsAgentRunRequestDTO request = input == null ? new OpsAgentRunRequestDTO() : input;
        String analysisId = hasText(request.getRunId())
                ? request.getRunId()
                : "ops_" + System.currentTimeMillis();
        return OpsAnalysisResponseDTO.builder()
                .analysisId(analysisId)
                .agentDefinitionId(request.getAgentDefinitionId())
                .agentVersion(request.getAgentVersion())
                .agentRuntime("")
                .rangeMinutes(request.getRangeMinutes())
                .promWindow(request.getPromWindow())
                .generatedAt(generatedAtSupplier.get())
                .elasticsearchStatus(status("Elasticsearch", esLogSettings.endpoint()))
                .prometheusStatus(status("Prometheus", prometheusSettings.baseUrl()))
                .mysqlSlowSqlStatus(status("MySQL Slow SQL", "mysql.slow_log/performance_schema"))
                .logSummary(emptyLogSummary())
                .metricSummary(emptyMetricSummary())
                .slowSqlSummary(emptySlowSqlSummary())
                .endpointMetrics(new ArrayList<>())
                .recentLogs(new ArrayList<>())
                .slowSqlSamples(new ArrayList<>())
                .insights(new ArrayList<>())
                .investigationResults(new ArrayList<>())
                .agentExecutionSteps(new ArrayList<>())
                .executionNotes(new ArrayList<>())
                .build();
    }

    private OpsAnalysisResponseDTO.DataSourceStatusDTO status(String name, String url) {
        return OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name(name)
                .url(url)
                .available(null)
                .message("未选择")
                .build();
    }

    private OpsAnalysisResponseDTO.LogSummaryDTO emptyLogSummary() {
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

    private OpsAnalysisResponseDTO.MetricSummaryDTO emptyMetricSummary() {
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

    private OpsAnalysisResponseDTO.SlowSqlSummaryDTO emptySlowSqlSummary() {
        return OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder()
                .totalStatements(0L)
                .slowStatements(0L)
                .avgQueryTimeMs(0D)
                .maxQueryTimeMs(0D)
                .rowsExamined(0L)
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
