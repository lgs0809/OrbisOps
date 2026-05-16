package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAgentCapabilityBindingRepositoryTest {

    @Test
    void replaceDeletesVersionBindingsAndWritesTypedValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentCapabilityBindingRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.replace(snapshot());

        verify(jdbc).update(argThat(sql -> sql.contains(
                        "DELETE FROM ai_ops_agent_capability_binding")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains(
                        "INSERT IGNORE INTO ai_ops_agent_capability_binding")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findLatestMapsStorageValuesToTypedBinding() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentCapabilityBindingRepository repository = repository(jdbc);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("agent_id")).thenReturn("agent-a");
        when(resultSet.getInt("version")).thenReturn(3);
        when(resultSet.getString("lifecycle")).thenReturn("PUBLISHED");
        when(resultSet.getString("project_id")).thenReturn("project-a");
        when(resultSet.getString("owner_type")).thenReturn("NODE");
        when(resultSet.getString("node_id")).thenReturn("diagnose");
        when(resultSet.getString("capability_type")).thenReturn("inline_mcp_server");
        when(resultSet.getString("capability_id")).thenReturn("inline-query");
        when(resultSet.getString("capability_scope")).thenReturn("INLINE");
        when(resultSet.getString("bind_config_json")).thenReturn("{\"transport\":\"http\"}");
        when(resultSet.getString("create_by")).thenReturn("system");
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.from(Instant.parse("2026-07-21T00:00:00Z")));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<AgentCapabilityBinding> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        AgentCapabilityBinding binding = repository.findLatest("agent-a").get(0);

        assertEquals(AgentCapabilityOwnerType.NODE, binding.ownerType());
        assertEquals(AgentCapabilityType.INLINE_MCP_SERVER, binding.capabilityType());
        assertEquals(AgentCapabilityScope.INLINE, binding.capabilityScope());
        assertEquals("http", binding.bindConfig().get("transport"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcStoreFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcAgentCapabilityBindingRepository repository =
                new JdbcAgentCapabilityBindingRepository(provider);

        assertThrows(IllegalStateException.class, () -> repository.replace(snapshot()));
    }

    @Test
    void replaceDeclaresMysqlTransactionBoundary() throws NoSuchMethodException {
        Transactional transactional = JdbcAgentCapabilityBindingRepository.class
                .getMethod("replace", AgentCapabilityBindingSnapshot.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals("mysqlTransactionManager", transactional.transactionManager());
    }

    private JdbcAgentCapabilityBindingRepository repository(JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcAgentDefinitionSchemaInitializer initializer =
                new JdbcAgentDefinitionSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", false);
        JdbcAgentCapabilityBindingRepository repository =
                new JdbcAgentCapabilityBindingRepository(provider, initializer);
        ReflectionTestUtils.setField(repository, "jdbcEnabled", true);
        return repository;
    }

    private AgentCapabilityBindingSnapshot snapshot() {
        AgentCapabilityBinding binding = AgentCapabilityBinding.create(
                "agent-a",
                3,
                AgentDefinitionLifecycle.PUBLISHED,
                "project-a",
                AgentCapabilityOwnerType.NODE,
                "diagnose",
                AgentCapabilityType.INLINE_MCP_SERVER,
                "inline-query",
                Map.of("transport", "http"));
        return new AgentCapabilityBindingSnapshot(
                "agent-a",
                3,
                AgentDefinitionLifecycle.PUBLISHED,
                "project-a",
                List.of(binding));
    }
}
