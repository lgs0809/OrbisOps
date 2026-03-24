package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionGraphRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.AgentScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.Edge;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.McpServerBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.Node;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.SkillBinding;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Repository
public class JdbcAgentDefinitionGraphRepository implements IAgentDefinitionGraphRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcAgentDefinitionSchemaInitializer schemaInitializer;

    @Value("${orbisops.agents.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    JdbcAgentDefinitionGraphRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this(jdbcTemplateProvider, null);
    }

    @Autowired
    public JdbcAgentDefinitionGraphRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcAgentDefinitionSchemaInitializer schemaInitializer) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schemaInitializer = schemaInitializer;
    }

    @Override
    public boolean available() {
        return jdbcEnabled && jdbcTemplateProvider != null && jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void replace(AgentDefinitionNormalizedGraphSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "AGENT_DEFINITION_GRAPH_SNAPSHOT_REQUIRED");
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        String agentId = snapshot.agentId();
        template.update("DELETE FROM ai_ops_agent_node WHERE agent_id = ?", agentId);
        template.update("DELETE FROM ai_ops_agent_edge WHERE agent_id = ?", agentId);
        template.update("DELETE FROM ai_ops_agentscope_agent WHERE agent_id = ?", agentId);
        template.update("DELETE FROM ai_ops_agent_skill_binding WHERE agent_id = ?", agentId);
        template.update("DELETE FROM ai_ops_agent_mcp_server WHERE agent_id = ?", agentId);

        for (Node node : snapshot.nodes()) {
            template.update("""
                            INSERT INTO ai_ops_agent_node
                            (agent_id, node_id, node_type, agent, sub_engine, output_key, rag_enabled, knowledge_base_id,
                             description, instruction, config_json, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    agentId,
                    node.nodeId(),
                    node.nodeType(),
                    node.agent(),
                    node.subEngine(),
                    node.outputKey(),
                    booleanValue(node.ragEnabled()),
                    node.knowledgeBaseId(),
                    node.description(),
                    node.instruction(),
                    JSON.toJSONString(node.config()),
                    node.sortOrder());
        }

        for (Edge edge : snapshot.edges()) {
            template.update("""
                            INSERT INTO ai_ops_agent_edge
                            (agent_id, edge_id, edge_name, from_node_id, to_node_id, condition_type, condition_expr,
                             default_edge, feedback_edge, priority_order, data_mapping_json, description, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    agentId,
                    edge.edgeId(),
                    edge.name(),
                    edge.fromNodeId(),
                    edge.toNodeId(),
                    edge.conditionType(),
                    edge.conditionExpression(),
                    booleanValue(edge.defaultEdge()),
                    booleanValue(edge.feedbackEdge()),
                    edge.priority(),
                    JSON.toJSONString(edge.dataMapping()),
                    edge.description(),
                    edge.sortOrder());
        }

        for (AgentScope scope : snapshot.agentScopes()) {
            template.update("""
                            INSERT INTO ai_ops_agentscope_agent
                            (agent_id, scope_agent_id, name, instruction, output_key, rag_enabled, knowledge_base_id,
                             max_iterations, max_depth, role, allowed_tools_json, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    agentId,
                    scope.scopeAgentId(),
                    scope.name(),
                    scope.instruction(),
                    scope.outputKey(),
                    booleanValue(scope.ragEnabled()),
                    scope.knowledgeBaseId(),
                    scope.maxIterations(),
                    scope.maxDepth(),
                    scope.role(),
                    JSON.toJSONString(scope.allowedToolNames()),
                    scope.sortOrder());
        }

        for (SkillBinding binding : snapshot.skillBindings()) {
            template.update("""
                            INSERT IGNORE INTO ai_ops_agent_skill_binding
                            (agent_id, owner_type, owner_id, skill_name)
                            VALUES (?, ?, ?, ?)
                            """,
                    agentId,
                    binding.ownerType().name(),
                    binding.ownerId(),
                    binding.skillName());
        }

        for (McpServerBinding binding : snapshot.mcpServerBindings()) {
            template.update("""
                            INSERT INTO ai_ops_agent_mcp_server
                            (agent_id, owner_type, owner_id, server_name, description, transport, command_text, url,
                             timeout_seconds, args_json, env_json, headers_json, tool_capabilities_json,
                             allowed_tools_json, notification_tools_json, blocked_tools_json)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    agentId,
                    binding.ownerType().name(),
                    binding.ownerId(),
                    binding.serverName(),
                    binding.description(),
                    binding.transport(),
                    binding.command(),
                    binding.url(),
                    binding.timeoutSeconds(),
                    JSON.toJSONString(binding.args()),
                    JSON.toJSONString(binding.env()),
                    JSON.toJSONString(binding.headers()),
                    JSON.toJSONString(binding.toolCapabilities()),
                    JSON.toJSONString(binding.allowedTools()),
                    JSON.toJSONString(binding.notificationTools()),
                    JSON.toJSONString(binding.blockedTools()));
        }
    }

    private JdbcTemplate requiredTemplate() {
        if (!jdbcEnabled || jdbcTemplateProvider == null) {
            throw new IllegalStateException("AGENT_DEFINITION_GRAPH_STORE_UNAVAILABLE");
        }
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("AGENT_DEFINITION_GRAPH_STORE_UNAVAILABLE");
        }
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) {
            schemaInitializer.initialize();
        }
    }

    private Integer booleanValue(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }
}
