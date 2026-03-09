package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcProjectMcpRepositoryTest {

    @Test
    void missingJdbcUsesInMemoryProjectMcpCatalog() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcProjectMcpRepository repository = new JdbcProjectMcpRepository(provider);

        ProjectMcpDefinition saved = repository.save(definition());

        assertEquals(saved, repository.find("project-1", "orders-mcp").orElseThrow());
        assertEquals(List.of(saved), repository.list("project-1"));
        assertEquals(List.of(saved), repository.listByTemplate("mysql-readonly-template"));
        assertEquals(List.of(saved), repository.listAll());
    }

    @Test
    void jdbcSaveUsesIdempotentUpsertAndKeepsMemoryFallback() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class))).thenReturn(List.of());
        JdbcProjectMcpRepository repository = new JdbcProjectMcpRepository(provider);

        ProjectMcpDefinition saved = repository.save(definition());

        assertEquals("orders-mcp", saved.mcpId());
        assertTrue(repository.find("project-1", "orders-mcp").isPresent());
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_project_mcp")
                                && sql.contains("ON DUPLICATE KEY UPDATE")
                                && sql.contains("permission_policy_json=VALUES(permission_policy_json)")),
                any(Object[].class));
    }

    private ProjectMcpDefinition definition() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        return new ProjectMcpDefinition(
                "orders-mcp",
                "Orders MCP",
                "project-1",
                "orders-db",
                "mysql",
                "stdio",
                "mysql-readonly-template",
                Map.of("generated", true),
                List.of("SELECT", "EXPLAIN"),
                cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel.LOW,
                true,
                Map.of("maxRows", 100),
                15,
                ProjectMcpStatus.ENABLED,
                now,
                now);
    }
}
