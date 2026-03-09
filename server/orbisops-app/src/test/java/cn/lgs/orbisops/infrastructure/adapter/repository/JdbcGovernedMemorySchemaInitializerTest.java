package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcGovernedMemorySchemaInitializerTest {

    @Test
    @SuppressWarnings("unchecked")
    void initializesAllGovernedMemoryTablesOnlyOnce() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcGovernedMemorySchemaInitializer initializer =
                new JdbcGovernedMemorySchemaInitializer(provider);

        initializer.initialize();
        initializer.initialize();

        verify(jdbc, times(1)).execute(argThat((String sql) ->
                sql.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory (")));
        verify(jdbc, times(1)).execute(argThat((String sql) ->
                sql.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory_version")));
        verify(jdbc, times(1)).execute(argThat((String sql) ->
                sql.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory_conflict")));
        verify(jdbc, times(1)).execute(argThat((String sql) ->
                sql.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory_audit")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcSkipsInitialization() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcGovernedMemorySchemaInitializer initializer =
                new JdbcGovernedMemorySchemaInitializer(provider);

        initializer.initialize();

        verify(provider).getIfAvailable();
    }
}
