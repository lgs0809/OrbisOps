package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpToolSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcMcpToolSnapshotRepositoryTest {

    @Test
    void savePersistsTypedSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcMcpToolSnapshotRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.save(snapshot());

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        JdbcMcpToolSnapshotRepository repository = new JdbcMcpToolSnapshotRepository();

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.save(snapshot()));
    }

    private JdbcMcpToolSnapshotRepository repository(JdbcTemplate jdbcTemplate) {
        JdbcMcpToolSnapshotRepository repository = new JdbcMcpToolSnapshotRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
        return repository;
    }

    private McpToolSnapshot snapshot() {
        return new McpToolSnapshot("snapshot-1", "project-1", "mcp-1", "tool-1", "search",
                "schema-1", "{\"schemaHydrated\":true}", "{}", true, "ACTIVE", null, null);
    }
}
