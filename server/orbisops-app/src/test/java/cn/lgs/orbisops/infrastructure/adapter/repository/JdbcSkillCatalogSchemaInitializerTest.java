package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JdbcSkillCatalogSchemaInitializerTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsCatalogVersionAndArtifactTablesWhenAutoInitEnabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        JdbcSkillCatalogSchemaInitializer initializer = new JdbcSkillCatalogSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", true);

        initializer.initialize();

        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_skill ("));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_skill_version"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_skill_artifact"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsInitializationWhenDisabledOrJdbcUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        JdbcSkillCatalogSchemaInitializer disabled = new JdbcSkillCatalogSchemaInitializer(provider);
        ReflectionTestUtils.setField(disabled, "autoInit", false);

        disabled.initialize();

        verifyNoInteractions(jdbcTemplate);

        ObjectProvider<JdbcTemplate> unavailableProvider = mock(ObjectProvider.class);
        JdbcSkillCatalogSchemaInitializer unavailable = new JdbcSkillCatalogSchemaInitializer(unavailableProvider);
        ReflectionTestUtils.setField(unavailable, "autoInit", true);
        assertDoesNotThrow(unavailable::initialize);
        verify(unavailableProvider).getIfAvailable();
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesLegacyDegradeBehaviorWhenCreateTableFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        doThrow(new DataAccessResourceFailureException("db unavailable"))
                .when(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_skill ("));
        JdbcSkillCatalogSchemaInitializer initializer = new JdbcSkillCatalogSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", true);

        assertDoesNotThrow(initializer::initialize);

        verify(jdbcTemplate, never()).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_skill_version"));
    }
}
