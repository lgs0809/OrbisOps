package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class JdbcAgentCapabilityBindingRepository implements IAgentCapabilityBindingRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcAgentDefinitionSchemaInitializer schemaInitializer;

    @Value("${orbisops.agents.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    JdbcAgentCapabilityBindingRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this(jdbcTemplateProvider, null);
    }

    @Autowired
    public JdbcAgentCapabilityBindingRepository(
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
    public List<AgentCapabilityBinding> findLatest(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        String normalizedAgentId = agentId.trim();
        return template.query("""
                        SELECT id, agent_id, version, lifecycle, project_id, owner_type, node_id,
                               capability_type, capability_id, capability_scope, bind_config_json,
                               create_by, create_time
                        FROM ai_ops_agent_capability_binding
                        WHERE agent_id = ?
                          AND version = (
                            SELECT COALESCE(MAX(version), 0)
                            FROM ai_ops_agent_capability_binding
                            WHERE agent_id = ?
                          )
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> {
                    Timestamp createTime = rs.getTimestamp("create_time");
                    return new AgentCapabilityBinding(
                            rs.getLong("id"),
                            rs.getString("agent_id"),
                            rs.getInt("version"),
                            AgentDefinitionLifecycle.require(rs.getString("lifecycle")),
                            rs.getString("project_id"),
                            AgentCapabilityOwnerType.require(rs.getString("owner_type")),
                            rs.getString("node_id"),
                            AgentCapabilityType.require(rs.getString("capability_type")),
                            rs.getString("capability_id"),
                            AgentCapabilityScope.require(rs.getString("capability_scope")),
                            parseBindConfig(rs.getString("bind_config_json")),
                            rs.getString("create_by"),
                            createTime == null ? null : createTime.toInstant());
                },
                normalizedAgentId,
                normalizedAgentId);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void replace(AgentCapabilityBindingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "AGENT_CAPABILITY_BINDING_SNAPSHOT_REQUIRED");
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update(
                "DELETE FROM ai_ops_agent_capability_binding WHERE agent_id = ? AND version = ?",
                snapshot.agentId(),
                snapshot.version());
        for (AgentCapabilityBinding binding : snapshot.bindings()) {
            template.update("""
                            INSERT IGNORE INTO ai_ops_agent_capability_binding
                            (agent_id, version, lifecycle, project_id, owner_type, node_id, capability_type,
                             capability_id, capability_scope, bind_config_json, create_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    binding.agentId(),
                    binding.version(),
                    binding.lifecycle().name(),
                    binding.projectId(),
                    binding.ownerType().name(),
                    binding.nodeId(),
                    binding.capabilityType().storageValue(),
                    binding.capabilityId(),
                    binding.capabilityScope().name(),
                    JSON.toJSONString(binding.bindConfig()),
                    binding.createBy());
        }
    }

    private JdbcTemplate requiredTemplate() {
        if (!jdbcEnabled || jdbcTemplateProvider == null) {
            throw new IllegalStateException("AGENT_CAPABILITY_BINDING_STORE_UNAVAILABLE");
        }
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("AGENT_CAPABILITY_BINDING_STORE_UNAVAILABLE");
        }
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) {
            schemaInitializer.initialize();
        }
    }

    private Map<String, Object> parseBindConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = JSON.parseObject(
                json,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        return parsed == null ? Map.of() : parsed;
    }
}
