package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsEsLogSettings;
import cn.lgs.orbisops.trigger.ops.OpsPrometheusSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisResponseShellFactoryTest {

    @Test
    void shouldCreateStableTypedDatasourceResponseShell() {
        OpsAnalysisResponseShellFactory factory = new OpsAnalysisResponseShellFactory(
                new OpsEsLogSettings("http://es:9200/", "logs-*", 5, 8),
                new OpsPrometheusSettings("http://prom:9090/", "demo-service", 5),
                () -> "2026-07-28 17:30:00");

        OpsAnalysisResponseDTO response = factory.create(OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .agentDefinitionId("ops-agent")
                .agentVersion(7)
                .rangeMinutes(30)
                .promWindow("10m")
                .build());

        assertAll(
                () -> assertEquals("run-1", response.getAnalysisId()),
                () -> assertEquals("ops-agent", response.getAgentDefinitionId()),
                () -> assertEquals(7, response.getAgentVersion()),
                () -> assertEquals("", response.getAgentRuntime()),
                () -> assertEquals(30, response.getRangeMinutes()),
                () -> assertEquals("10m", response.getPromWindow()),
                () -> assertEquals("2026-07-28 17:30:00", response.getGeneratedAt()),
                () -> assertEquals("http://es:9200/logs-*", response.getElasticsearchStatus().getUrl()),
                () -> assertEquals("http://prom:9090", response.getPrometheusStatus().getUrl()),
                () -> assertEquals("mysql.slow_log/performance_schema", response.getMysqlSlowSqlStatus().getUrl()),
                () -> assertNull(response.getElasticsearchStatus().getAvailable()),
                () -> assertEquals("未选择", response.getElasticsearchStatus().getMessage()),
                () -> assertEquals(0L, response.getLogSummary().getTotalLogs()),
                () -> assertEquals(0L, response.getLogSummary().getLevelCounts().get("ERROR")),
                () -> assertEquals(0, response.getMetricSummary().getInstanceTotal()),
                () -> assertEquals(0L, response.getSlowSqlSummary().getTotalStatements()),
                () -> assertTrue(response.getEndpointMetrics().isEmpty()),
                () -> assertTrue(response.getRecentLogs().isEmpty()),
                () -> assertTrue(response.getSlowSqlSamples().isEmpty()),
                () -> assertTrue(response.getInsights().isEmpty()),
                () -> assertTrue(response.getInvestigationResults().isEmpty()),
                () -> assertTrue(response.getAgentExecutionSteps().isEmpty()),
                () -> assertTrue(response.getExecutionNotes().isEmpty()));
    }

    @Test
    void nullSettingsAndRequestShouldUseStableDefaults() {
        OpsAnalysisResponseShellFactory factory = new OpsAnalysisResponseShellFactory(
                null,
                null,
                () -> "fixed");

        OpsAnalysisResponseDTO response = factory.create(null);

        assertAll(
                () -> assertTrue(response.getAnalysisId().startsWith("ops_")),
                () -> assertEquals("http://127.0.0.1:9200", response.getElasticsearchStatus().getUrl()),
                () -> assertEquals("http://127.0.0.1:9090", response.getPrometheusStatus().getUrl()),
                () -> assertFalse(response.getLogSummary().getLevelCounts().isEmpty()));
    }
}
