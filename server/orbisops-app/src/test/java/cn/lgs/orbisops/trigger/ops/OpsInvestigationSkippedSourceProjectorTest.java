package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpsInvestigationSkippedSourceProjectorTest {

    private final OpsInvestigationSkippedSourceProjector projector =
            new OpsInvestigationSkippedSourceProjector();

    @Test
    void projectsAllNonExecutedSourcesWithConfiguredEndpoints() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .skippedTasks(List.of(
                                skipped("elasticsearch", "无需日志"),
                                skipped("prometheus", "无需指标")))
                        .build();

        projector.project(
                response,
                Set.of(),
                plan,
                new OpsEsLogSettings("http://es:9200/", "logs-*", 5, 8),
                new OpsPrometheusSettings("http://prom:9090/", "demo-service", 5));

        assertEquals("Elasticsearch", response.getElasticsearchStatus().getName());
        assertEquals("http://es:9200/logs-*", response.getElasticsearchStatus().getUrl());
        assertNull(response.getElasticsearchStatus().getAvailable());
        assertEquals("未选择：无需日志", response.getElasticsearchStatus().getMessage());

        assertEquals("http://prom:9090", response.getPrometheusStatus().getUrl());
        assertEquals("未选择：无需指标", response.getPrometheusStatus().getMessage());

        assertEquals("mysql.slow_log/performance_schema", response.getMysqlSlowSqlStatus().getUrl());
        assertEquals("未选择：主 Agent 判断本轮没有足够信息增益。", response.getMysqlSlowSqlStatus().getMessage());
    }

    @Test
    void executedSourceKeepsExistingStatusUntouched() {
        OpsAnalysisResponseDTO.DataSourceStatusDTO existing =
                OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                        .name("Elasticsearch")
                        .message("executed")
                        .available(true)
                        .build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .elasticsearchStatus(existing)
                .build();

        projector.project(
                response,
                Set.of("elasticsearch"),
                new OpsAnalysisResponseDTO.OpsInvestigationPlanDTO(),
                new OpsEsLogSettings("http://es:9200", "logs-*", 5, 8),
                new OpsPrometheusSettings("http://prom:9090", "demo-service", 5));

        assertSame(existing, response.getElasticsearchStatus());
        assertEquals("executed", response.getElasticsearchStatus().getMessage());
    }

    @Test
    void matchingSkippedTaskUsesNullReasonExactlyAsLegacyProjection() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .skippedTasks(List.of(skipped("prometheus", null)))
                        .build();

        projector.project(
                response,
                Set.of("elasticsearch", "mysql_slow_sql"),
                plan,
                OpsEsLogSettings.defaults(),
                new OpsPrometheusSettings("http://prom:9090", "demo-service", 5));

        assertEquals("未选择：null", response.getPrometheusStatus().getMessage());
    }

    @Test
    void nullPlanAndSettingsUseStableDefaults() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();

        projector.project(
                response,
                Set.of("prometheus", "mysql_slow_sql"),
                null,
                null,
                null);

        assertEquals(
                "未选择：主 Agent 判断本轮没有足够信息增益。",
                response.getElasticsearchStatus().getMessage());
        assertEquals("http://127.0.0.1:9200", response.getElasticsearchStatus().getUrl());
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO skipped(
            String source,
            String reason) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .reason(reason)
                .build();
    }
}
