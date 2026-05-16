package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAnalysisAuditSchemaInitializerTest {

    @Test
    void infrastructureOwnsAnalysisAuditDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = provider(jdbc);
        JdbcAnalysisAuditSchemaInitializer initializer =
                new JdbcAnalysisAuditSchemaInitializer(provider, true, true);

        initializer.initialize();

        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_audit"));
    }

    @Test
    void disabledOrManualSchemaModeSkipsDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new JdbcAnalysisAuditSchemaInitializer(provider(jdbc), false, true).initialize();
        new JdbcAnalysisAuditSchemaInitializer(provider(jdbc), true, false).initialize();

        verify(jdbc, never()).execute(contains("ai_ops_agent_audit"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }
}
