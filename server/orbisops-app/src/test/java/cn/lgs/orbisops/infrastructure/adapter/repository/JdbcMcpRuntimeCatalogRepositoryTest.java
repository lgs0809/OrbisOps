package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcMcpRuntimeCatalogRepositoryTest {

    @Test
    void saveActivationPersistsTypedRecord() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcMcpRuntimeCatalogRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.saveActivation(activation());

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        JdbcMcpRuntimeCatalogRepository repository = new JdbcMcpRuntimeCatalogRepository();

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.saveActivation(activation()));
    }

    private JdbcMcpRuntimeCatalogRepository repository(JdbcTemplate jdbcTemplate) {
        JdbcMcpRuntimeCatalogRepository repository = new JdbcMcpRuntimeCatalogRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
        return repository;
    }

    private McpRuntimeActivation activation() {
        return new McpRuntimeActivation("activation-1", "project-1", "run-1", "session-1", "agent-1",
                "mcp-1", "search", "schema-1", "EXTENSION", "ACTIVE",
                LocalDateTime.now().plusHours(2), "{}", null, null);
    }
}
