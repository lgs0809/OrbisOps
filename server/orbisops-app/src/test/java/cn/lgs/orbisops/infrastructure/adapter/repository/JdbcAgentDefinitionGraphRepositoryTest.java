package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.AgentScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.Edge;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.McpServerBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.Node;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.OwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.SkillBinding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

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

class JdbcAgentDefinitionGraphRepositoryTest {

    @Test
    void replaceDeletesOldGraphAndWritesEveryNormalizedPart() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAgentDefinitionGraphRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.replace(snapshot());

        verify(jdbc).update(argThat((String sql) -> sql.contains("DELETE FROM ai_ops_agent_node")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("DELETE FROM ai_ops_agent_edge")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("DELETE FROM ai_ops_agentscope_agent")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("DELETE FROM ai_ops_agent_skill_binding")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("DELETE FROM ai_ops_agent_mcp_server")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("INSERT INTO ai_ops_agent_node")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("INSERT INTO ai_ops_agent_edge")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("INSERT INTO ai_ops_agentscope_agent")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("INSERT IGNORE INTO ai_ops_agent_skill_binding")), any(Object[].class));
        verify(jdbc).update(argThat((String sql) -> sql.contains("INSERT INTO ai_ops_agent_mcp_server")), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcStoreFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcAgentDefinitionGraphRepository repository =
                new JdbcAgentDefinitionGraphRepository(provider);

        assertThrows(IllegalStateException.class, () -> repository.replace(snapshot()));
    }

    @Test
    void replaceDeclaresMysqlTransactionBoundary() throws NoSuchMethodException {
        Transactional transactional = JdbcAgentDefinitionGraphRepository.class
                .getMethod("replace", AgentDefinitionNormalizedGraphSnapshot.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals("mysqlTransactionManager", transactional.transactionManager());
    }

    private JdbcAgentDefinitionGraphRepository repository(JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcAgentDefinitionSchemaInitializer initializer =
                new JdbcAgentDefinitionSchemaInitializer(provider);
        ReflectionTestUtils.setField(initializer, "autoInit", false);
        JdbcAgentDefinitionGraphRepository repository =
                new JdbcAgentDefinitionGraphRepository(provider, initializer);
        ReflectionTestUtils.setField(repository, "jdbcEnabled", true);
        return repository;
    }

    private AgentDefinitionNormalizedGraphSnapshot snapshot() {
        return new AgentDefinitionNormalizedGraphSnapshot(
                "agent-a",
                List.of(new Node(
                        "node-a", "CHAT", "assistant", null, "answer", true,
                        "kb-a", "node", "instruction", Map.of("temperature", 0.2), 0)),
                List.of(new Edge(
                        "edge-a", "next", "start", "node-a", "always", "always",
                        true, false, 1, Map.of("input", "question"), "edge", 0)),
                List.of(new AgentScope(
                        "scope-a", "specialist", "scope instruction", "scope-output", false,
                        null, 3, 2, "DIAGNOSE", List.of("read-only-query"), 0)),
                List.of(new SkillBinding(OwnerType.NODE, "node-a", "diagnosis")),
                List.of(new McpServerBinding(
                        OwnerType.AGENT, "agent-a", "readonly-mcp", "readonly", "http",
                        null, "http://localhost/mcp", 30, List.of("--safe"),
                        Map.of("ENV", "test"), Map.of("Authorization", "masked"),
                        Map.of("query", "read"), List.of("query"), List.of(), List.of("write"))));
    }
}
