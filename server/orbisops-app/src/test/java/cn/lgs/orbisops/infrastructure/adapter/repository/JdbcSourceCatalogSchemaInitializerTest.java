package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSourceCatalogSchemaInitializerTest {

    @Test
    void infrastructureOwnsAllSourceCatalogDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSourceCatalogSchemaInitializer initializer =
                new JdbcSourceCatalogSchemaInitializer(provider(jdbc), true, true, true);

        initializer.initialize();

        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_source_repository"));
        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_deployment_revision"));
        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_project_service"));
    }

    @Test
    void disabledAndManualModesSkipDdlWhileEnabledMissingJdbcFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new JdbcSourceCatalogSchemaInitializer(provider(jdbc), false, true, true).initialize();
        new JdbcSourceCatalogSchemaInitializer(provider(jdbc), true, true, false).initialize();
        verify(jdbc, never()).execute(contains("ai_ops_source_repository"));

        assertEquals("代码仓库配置必须使用 MySQL 持久化",
                assertThrows(IllegalStateException.class,
                        () -> new JdbcSourceCatalogSchemaInitializer(
                                provider(null), true, true, true).initialize()).getMessage());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }
}
