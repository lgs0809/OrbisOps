package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishConflict;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAgentDefinitionRepositoryTest {

    @Test
    void savePublishedVersionUpdatesVersionAndCurrentPointers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentDefinitionRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.saveVersion(snapshot(2, "hash-2", AgentDefinitionLifecycle.PUBLISHED, true));

        verify(jdbc).update(argThat(sql -> sql.contains("SET current_published = 0")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_agent_definition_version")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_agent_definition\n")),
                any(Object[].class));
    }

    @Test
    void publishReturnsAlreadyCurrentWithoutMutatingVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentDefinitionRepository repository = repository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("version", 2, "definition_hash", "hash-2", "enabled", 1)));

        AgentDefinitionPublishResult result = repository.publish(
                snapshot(2, "hash-2", AgentDefinitionLifecycle.PUBLISHED, true),
                "hash-2");

        assertEquals(AgentDefinitionPublishResult.ALREADY_CURRENT, result);
    }

    @Test
    void publishRaisesTypedVersionConflictWhenCandidateChanged() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentDefinitionRepository repository = repository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("version", 1, "definition_hash", "hash-1", "enabled", 1)));
        when(jdbc.update(argThat(sql -> sql.contains("SET lifecycle='PUBLISHED'")),
                any(Object[].class))).thenReturn(0);

        AgentDefinitionPublishConflict conflict = assertThrows(
                AgentDefinitionPublishConflict.class,
                () -> repository.publish(
                        snapshot(2, "hash-2", AgentDefinitionLifecycle.PUBLISHED, true),
                        "validated-hash"));

        assertEquals(AgentDefinitionPublishConflict.Reason.VERSION_CHANGED, conflict.reason());
    }

    @Test
    void publishRaisesTypedPointerConflictWhenCurrentChanged() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentDefinitionRepository repository = repository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("version", 1, "definition_hash", "hash-1", "enabled", 1)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.update(argThat(sql -> sql.contains("WHERE agent_id=? AND version=? AND definition_hash=?")),
                any(Object[].class))).thenReturn(0);

        AgentDefinitionPublishConflict conflict = assertThrows(
                AgentDefinitionPublishConflict.class,
                () -> repository.publish(
                        snapshot(2, "hash-2", AgentDefinitionLifecycle.PUBLISHED, true),
                        "validated-hash"));

        assertEquals(AgentDefinitionPublishConflict.Reason.CURRENT_POINTER_CHANGED, conflict.reason());
    }

    @Test
    void publishReactivatesDisabledCurrentPointerWithCas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentDefinitionRepository repository = repository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("version", 1, "definition_hash", "hash-1", "enabled", 0)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        AgentDefinitionPublishResult result = repository.publish(
                snapshot(2, "hash-2", AgentDefinitionLifecycle.PUBLISHED, true),
                "validated-hash");

        assertEquals(AgentDefinitionPublishResult.PUBLISHED, result);
        verify(jdbc).update(argThat(sql -> sql.contains(
                        "WHERE agent_id=? AND version=? AND definition_hash=? AND enabled=?")),
                any(Object[].class));
        verify(jdbc, never()).update(argThat(sql -> sql.contains(
                        "INSERT IGNORE INTO ai_ops_agent_definition")),
                any(Object[].class));
    }

    @Test
    void publishDeclaresMysqlTransactionBoundary() throws NoSuchMethodException {
        Transactional transactional = JdbcAgentDefinitionRepository.class
                .getMethod("publish", AgentDefinitionSnapshot.class, String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals("mysqlTransactionManager", transactional.transactionManager());
    }

    @Test
    void repositoryDelegatesSchemaLifecycleToInitializer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0)
                        .contains("information_schema.columns") ? 1 : 0);
        JdbcAgentDefinitionSchemaInitializer initializer =
                new JdbcAgentDefinitionSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", true);
        JdbcAgentDefinitionRepository repository =
                new JdbcAgentDefinitionRepository(provider, initializer);
        ReflectionTestUtils.setField(repository, "jdbcEnabled", true);

        assertEquals(0, repository.maxVersion("agent-a"));

        verify(jdbc).execute(argThat((String sql) -> sql.contains(
                "CREATE TABLE IF NOT EXISTS ai_ops_agent_definition (")));
    }

    @Test
    void missingJdbcStoreIsUnavailableAndWritesFailClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcAgentDefinitionRepository repository = new JdbcAgentDefinitionRepository(provider);

        assertFalse(repository.available());
        assertTrue(repository.listCurrentEnabled().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> repository.saveVersion(snapshot(
                        1, "hash-1", AgentDefinitionLifecycle.DRAFT, false)));
    }

    private JdbcAgentDefinitionRepository repository(JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcAgentDefinitionRepository repository = new JdbcAgentDefinitionRepository(provider);
        ReflectionTestUtils.setField(repository, "jdbcEnabled", true);
        return repository;
    }

    private AgentDefinitionSnapshot snapshot(int version,
                                               String hash,
                                               AgentDefinitionLifecycle lifecycle,
                                               boolean current) {
        return new AgentDefinitionSnapshot(
                "agent-a",
                version,
                hash,
                lifecycle,
                "Agent A",
                "project-a",
                "CHAT",
                "description",
                "instruction",
                "start",
                "{\"agentId\":\"agent-a\"}",
                true,
                current,
                "UI");
    }
}
