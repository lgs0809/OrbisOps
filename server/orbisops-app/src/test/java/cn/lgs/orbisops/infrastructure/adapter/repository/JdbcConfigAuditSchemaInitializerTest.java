package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConfigAuditSchemaInitializerTest {

    @Test
    void infrastructureOwnsAuditAndPolicyDdlAndCompatibilityInspection() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        JdbcConfigAuditSchemaInitializer initializer = new JdbcConfigAuditSchemaInitializer(provider, true);

        initializer.initialize();

        verify(jdbc, atLeastOnce()).execute(org.mockito.ArgumentMatchers.contains(
                "CREATE TABLE IF NOT EXISTS ai_ops_config_audit"));
        verify(jdbc, atLeastOnce()).execute(org.mockito.ArgumentMatchers.contains(
                "CREATE TABLE IF NOT EXISTS ai_ops_audit_policy"));
        verify(jdbc, atLeastOnce()).queryForObject(
                org.mockito.ArgumentMatchers.contains("information_schema.COLUMNS"),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.any(Object[].class));
    }
}
