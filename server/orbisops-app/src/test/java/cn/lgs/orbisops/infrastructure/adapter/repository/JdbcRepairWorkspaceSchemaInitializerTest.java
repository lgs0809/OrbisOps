package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcRepairWorkspaceSchemaInitializerTest {

    @Test
    void infrastructureOwnsWorkspaceDdlAndCompatibilityColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        JdbcRepairWorkspaceSchemaInitializer initializer =
                new JdbcRepairWorkspaceSchemaInitializer(provider(jdbc), true, true);

        initializer.initialize();

        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_repair_workspace"));
        verify(jdbc, org.mockito.Mockito.atLeast(6))
                .execute(contains("ALTER TABLE ai_ops_repair_workspace ADD COLUMN"));
    }

    @Test
    void disabledOrManualModeSkipsDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new JdbcRepairWorkspaceSchemaInitializer(provider(jdbc), false, true).initialize();
        new JdbcRepairWorkspaceSchemaInitializer(provider(jdbc), true, false).initialize();

        verify(jdbc, never()).execute(anyString());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }
}
