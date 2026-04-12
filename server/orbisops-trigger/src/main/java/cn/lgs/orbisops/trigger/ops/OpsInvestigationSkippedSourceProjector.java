package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.List;
import java.util.Set;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_ES;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_PROM;

/** Projects non-executed Investigation sources into response datasource status. */
final class OpsInvestigationSkippedSourceProjector {

    void project(OpsAnalysisResponseDTO response,
                 Set<String> executedSources,
                 OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                 OpsEsLogSettings esLogSettings,
                 OpsPrometheusSettings prometheusSettings) {
        Set<String> executed = executedSources == null ? Set.of() : executedSources;
        OpsEsLogSettings effectiveEsSettings = esLogSettings == null
                ? OpsEsLogSettings.defaults()
                : esLogSettings;
        OpsPrometheusSettings effectivePrometheusSettings = prometheusSettings == null
                ? OpsPrometheusSettings.defaults()
                : prometheusSettings;
        if (!executed.contains(SOURCE_ES)) {
            response.setElasticsearchStatus(status(
                    "Elasticsearch",
                    effectiveEsSettings.endpoint(),
                    "未选择：" + skippedReason(plan, SOURCE_ES)));
        }
        if (!executed.contains(SOURCE_PROM)) {
            response.setPrometheusStatus(status(
                    "Prometheus",
                    effectivePrometheusSettings.baseUrl(),
                    "未选择：" + skippedReason(plan, SOURCE_PROM)));
        }
        if (!executed.contains(SOURCE_MYSQL_SLOW_SQL)) {
            response.setMysqlSlowSqlStatus(status(
                    "MySQL Slow SQL",
                    "mysql.slow_log/performance_schema",
                    "未选择：" + skippedReason(plan, SOURCE_MYSQL_SLOW_SQL)));
        }
    }

    private String skippedReason(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            String source) {
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> skippedTasks =
                plan == null || plan.getSkippedTasks() == null
                        ? List.of()
                        : plan.getSkippedTasks();
        for (OpsAnalysisResponseDTO.InvestigationTaskDTO task : skippedTasks) {
            if (task != null && source.equals(task.getSource())) {
                return task.getReason();
            }
        }
        return "主 Agent 判断本轮没有足够信息增益。";
    }

    private OpsAnalysisResponseDTO.DataSourceStatusDTO status(
            String name,
            String url,
            String message) {
        return OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name(name)
                .url(url)
                .available(null)
                .message(message)
                .build();
    }

}
