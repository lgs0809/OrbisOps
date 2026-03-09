package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.incident.model.IncidentProductMetricsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class JdbcIncidentProductMetricsMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("incident_metrics_test")
            .withUsername("agent")
            .withPassword("agent");

    private JdbcTemplate jdbc;
    private JdbcIncidentRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcIncidentSchemaInitializer initializer = new JdbcIncidentSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", true);
        initializer.initialize();
        repository = new JdbcIncidentRepository(provider);
        jdbc.update("DELETE FROM ai_ops_incident_relation");
        jdbc.update("DELETE FROM ai_ops_incident_watcher");
        jdbc.update("DELETE FROM ai_ops_incident_run");
        jdbc.update("DELETE FROM ai_ops_incident_timeline");
        jdbc.update("DELETE FROM ai_ops_incident");
    }

    @Test
    void productMetricProjectionExecutesInRealMySqlAndRespectsLatestOccurrence() {
        insertIncident("incident-old", "2026-08-01 00:00:00");
        event("incident-old", "ANALYSIS_LINKED", "2026-08-01 00:01:00");
        event("incident-old", "VERIFICATION_SUCCEEDED", "2026-08-01 00:03:00");
        event("incident-old", "USER_CONFIRMED_HELPFUL", "2026-08-01 00:04:00");
        event("incident-old", "INCIDENT_REOPENED", "2026-08-02 00:00:00");

        insertIncident("incident-current", "2026-08-03 00:00:00");
        event("incident-current", "ANALYSIS_LINKED", "2026-08-03 00:01:00");
        event("incident-current", "DIAGNOSIS_ACTION_REQUIRED", "2026-08-03 00:02:00");
        event("incident-current", "VERIFICATION_SUCCEEDED", "2026-08-03 00:05:00");
        event("incident-current", "USER_CONFIRMED_HELPFUL", "2026-08-03 00:06:00");
        event("incident-current", "COMMENT", "2026-08-03 00:02:30");

        IncidentProductMetricsProjection projection = repository.productMetrics("2026-08-01 00:00:00");

        assertEquals(2L, projection.incidentCount());
        assertEquals(1L, projection.weeklyHelpfulResolvedIncidents());
        assertEquals(1L, projection.verificationSuccessCount());
        assertEquals(1L, projection.commentCount());
        assertNotNull(projection.averageMttaMs());
        assertNotNull(projection.averageDiagnosisDurationMs());
        assertNotNull(projection.averageMttrMs());
    }

    private void insertIncident(String incidentId, String firstSeenAt) {
        jdbc.update("""
                INSERT INTO ai_ops_incident
                  (incident_id, project_id, title, status, first_seen_at, last_seen_at, create_time, update_time)
                VALUES (?, 'project-a', ?, 'OPEN', ?, ?, ?, ?)
                """, incidentId, incidentId, firstSeenAt, firstSeenAt, firstSeenAt, firstSeenAt);
    }

    private void event(String incidentId, String eventType, String createTime) {
        jdbc.update("""
                INSERT INTO ai_ops_incident_timeline
                  (incident_id, event_type, title, detail, actor, ref_type, ref_id, payload_json, create_time)
                VALUES (?, ?, ?, '', 'test', 'INCIDENT', ?, '{}', ?)
                """, incidentId, eventType, eventType, incidentId, createTime);
    }
}
