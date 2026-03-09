package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.infrastructure.adapter.repository.JdbcToolExecutionEmergencyStopAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class OpsToolExecutionEmergencyStopControlMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("emergency_stop_test")
            .withUsername("agent")
            .withPassword("agent");

    @Test
    @SuppressWarnings("unchecked")
    void activationSurvivesControlRestartAndReleaseRestoresOnlyNewWrites() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        List<ToolExecutionAuditPort.ToolExecutionAuditEvent> audit = new ArrayList<>();
        JdbcToolExecutionEmergencyStopAdapter first = new JdbcToolExecutionEmergencyStopAdapter(
                provider, audit::add, false, true);
        first.initialize();
        first.set("project-1", true, "capacity drill", "operator");

        JdbcToolExecutionEmergencyStopAdapter restarted = new JdbcToolExecutionEmergencyStopAdapter(
                provider, audit::add, false, false);
        restarted.initialize();

        assertTrue(restarted.blocks(request(), writeTarget()));
        assertFalse(restarted.blocks(request(), readTarget()));
        restarted.set("project-1", false, "drill complete", "operator");
        assertFalse(restarted.blocks(request(), writeTarget()));
        assertTrue(audit.stream().anyMatch(event ->
                "tool_execution_emergency_stop_activated".equals(event.action())));
        assertTrue(audit.stream().anyMatch(event ->
                "tool_execution_emergency_stop_released".equals(event.action())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unavailableDurableStateFailsClosedForWritesButKeepsReadsAvailable() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcToolExecutionEmergencyStopAdapter control = new JdbcToolExecutionEmergencyStopAdapter(
                provider, event -> { }, false, false);
        control.initialize();

        assertTrue(control.blocks(request(), writeTarget()));
        assertFalse(control.blocks(request(), readTarget()));
    }

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1", "operator", "operator", "prod", "operation",
                ToolExecutionScope.APPROVED_LANDING, Map.of(), "session", "run",
                Map.of(), Map.of());
    }

    private ToolExecutionTarget writeTarget() {
        return new ToolExecutionTarget(
                "prod", "operation", "MCP", "HIGH",
                false, false, true, true, true);
    }

    private ToolExecutionTarget readTarget() {
        return new ToolExecutionTarget(
                "prod", "read", "MCP", "LOW",
                true, false, false, false, false);
    }
}
