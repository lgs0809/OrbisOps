package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JdbcAgentDefinitionSchemaInitializerTest {

    @Test
    @SuppressWarnings("unchecked")
    void skipsInitializationWhenAutoInitDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        JdbcAgentDefinitionSchemaInitializer initializer =
                new JdbcAgentDefinitionSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", false);

        initializer.initialize();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void safelySkipsInitializationWhenJdbcTemplateUnavailable() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcAgentDefinitionSchemaInitializer initializer =
                new JdbcAgentDefinitionSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", true);

        assertDoesNotThrow(initializer::initialize);

        verify(provider).getIfAvailable();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsAllAgentDefinitionSchemaTables() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        JdbcAgentDefinitionSchemaInitializer initializer = initializer(jdbcTemplate, true);

        initializer.initialize();

        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_definition ("));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_definition_version"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_node"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_edge"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agentscope_agent"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_skill_binding"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_mcp_server"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_capability_binding"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotAlterColumnsThatAlreadyExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        JdbcAgentDefinitionSchemaInitializer initializer = initializer(jdbcTemplate, true);

        initializer.initialize();

        verify(jdbcTemplate, never()).execute(contains("ALTER TABLE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsOnlyColumnsReportedMissingByInformationSchema() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2);
                    String columnName = invocation.getArgument(3);
                    return "ai_ops_agent_definition".equals(tableName)
                            && "name".equals(columnName) ? 0 : 1;
                });
        JdbcAgentDefinitionSchemaInitializer initializer = initializer(jdbcTemplate, true);

        initializer.initialize();

        verify(jdbcTemplate).execute(contains(
                "ALTER TABLE ai_ops_agent_definition ADD COLUMN name VARCHAR(128)"));
        verify(jdbcTemplate, never()).execute(contains(
                "ALTER TABLE ai_ops_agent_definition ADD COLUMN project_id"));
    }

    @SuppressWarnings("unchecked")
    private JdbcAgentDefinitionSchemaInitializer initializer(JdbcTemplate jdbcTemplate,
                                                              boolean autoInit) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        JdbcAgentDefinitionSchemaInitializer initializer =
                new JdbcAgentDefinitionSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", autoInit);
        return initializer;
    }
}
