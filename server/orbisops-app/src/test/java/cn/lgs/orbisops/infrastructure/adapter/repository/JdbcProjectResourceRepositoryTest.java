package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
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

class JdbcProjectResourceRepositoryTest {

    @Test
    void missingJdbcUsesInMemoryResourceCatalog() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcProjectResourceRepository repository =
                new JdbcProjectResourceRepository(provider);

        ProjectResourceDefinition saved = repository.save(resource());

        assertEquals("resource-1", saved.resourceId());
        assertEquals(saved, repository.find("project-1", "resource-1").orElseThrow());
        assertEquals(List.of(saved), repository.list("project-1"));
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
        JdbcProjectResourceRepository repository =
                new JdbcProjectResourceRepository(provider);

        ProjectResourceDefinition saved = repository.save(resource());

        assertEquals("resource-1", saved.resourceId());
        assertTrue(repository.find("project-1", "resource-1").isPresent());
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_project_resource")
                                && sql.contains("ON DUPLICATE KEY UPDATE")
                                && sql.contains("permission_json=VALUES(permission_json)")),
                any(Object[].class));
    }

    private ProjectResourceDefinition resource() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        return new ProjectResourceDefinition(
                "resource-1",
                "project-1",
                ProjectResourceType.from("mysql"),
                "MySQL",
                "Orders DB",
                "prod",
                "mysql://127.0.0.1:3306/orders",
                Map.of("username", "reader", "passwordRef", "${env:DB_PASSWORD}"),
                "PREVIEW",
                Map.of("source", "preview", "objects", List.of()),
                Map.of("readOnly", true, "maxRows", 100),
                now,
                now);
    }
}
