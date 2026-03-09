package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcContextMemorySchemaInitializerTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsContextMemoryTableOnlyOnce() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcContextMemorySchemaInitializer initializer =
                new JdbcContextMemorySchemaInitializer(provider);

        initializer.initialize();
        initializer.initialize();

        verify(jdbc, times(1)).execute(argThat((String sql) ->
                sql.contains("CREATE TABLE IF NOT EXISTS ai_ops_context_memory")));
    }
}
