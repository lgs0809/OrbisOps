package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishConflict;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcAgentDefinitionRepository implements IAgentDefinitionRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcAgentDefinitionSchemaInitializer schemaInitializer;

    @Value("${orbisops.agents.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    JdbcAgentDefinitionRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this(jdbcTemplateProvider, null);
    }

    @Autowired
    public JdbcAgentDefinitionRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcAgentDefinitionSchemaInitializer schemaInitializer) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schemaInitializer = schemaInitializer;
    }

    @Override
    public boolean available() {
        return availableTemplate() != null;
    }

    @Override
    public List<AgentDefinitionSnapshot> listCurrentEnabled() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureSchema();
            return template.query("""
                            SELECT agent_id, version, definition_hash,
                                   COALESCE(lifecycle, 'PUBLISHED') AS lifecycle,
                                   COALESCE(name, '') AS name,
                                   COALESCE(project_id, '') AS project_id,
                                   COALESCE(engine, '') AS engine,
                                   COALESCE(description, '') AS description,
                                   COALESCE(instruction, '') AS instruction,
                                   COALESCE(start_node_id, '') AS start_node_id,
                                   definition_json, enabled, 1 AS current_published,
                                   COALESCE(source, 'UI') AS source
                            FROM ai_ops_agent_definition
                            WHERE enabled = 1
                            ORDER BY id ASC
                            """,
                    this::snapshot);
        } catch (DataAccessException error) {
            log.warn("加载 Agent Definition current 失败 reason={}", error.getMessage());
            return List.of();
        }
    }

    @Override
    public List<AgentDefinitionSnapshot> listVersionsEnabled() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureSchema();
            return template.query("""
                            SELECT agent_id, version, definition_hash, lifecycle,
                                   COALESCE(name, '') AS name,
                                   COALESCE(project_id, '') AS project_id,
                                   COALESCE(engine, '') AS engine,
                                   COALESCE(description, '') AS description,
                                   COALESCE(instruction, '') AS instruction,
                                   COALESCE(start_node_id, '') AS start_node_id,
                                   definition_json, enabled, current_published,
                                   COALESCE(source, 'UI') AS source
                            FROM ai_ops_agent_definition_version
                            WHERE enabled = 1
                            ORDER BY agent_id ASC, version ASC
                            """,
                    this::snapshot);
        } catch (DataAccessException error) {
            log.warn("加载 Agent Definition version 失败 reason={}", error.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<AgentDefinitionSnapshot> findVersion(String agentId, int version) {
        if (!hasText(agentId) || version <= 0) {
            return Optional.empty();
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return Optional.empty();
        }
        try {
            ensureSchema();
            List<AgentDefinitionSnapshot> rows = template.query("""
                            SELECT agent_id, version, definition_hash, lifecycle,
                                   COALESCE(name, '') AS name,
                                   COALESCE(project_id, '') AS project_id,
                                   COALESCE(engine, '') AS engine,
                                   COALESCE(description, '') AS description,
                                   COALESCE(instruction, '') AS instruction,
                                   COALESCE(start_node_id, '') AS start_node_id,
                                   definition_json, enabled, current_published,
                                   COALESCE(source, 'UI') AS source
                            FROM ai_ops_agent_definition_version
                            WHERE agent_id = ? AND version = ? AND enabled = 1
                            LIMIT 1
                            """,
                    this::snapshot,
                    agentId.trim(),
                    version);
            return rows.stream().findFirst();
        } catch (DataAccessException error) {
            log.warn("查询 Agent Definition version 失败 agentId={} version={} reason={}",
                    agentId, version, error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int maxVersion(String agentId) {
        if (!hasText(agentId)) {
            return 0;
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return 0;
        }
        try {
            ensureSchema();
            Integer value = template.queryForObject("""
                            SELECT COALESCE(MAX(version), 0)
                            FROM ai_ops_agent_definition_version
                            WHERE agent_id = ?
                            """,
                    Integer.class,
                    agentId.trim());
            return value == null ? 0 : value;
        } catch (DataAccessException error) {
            log.warn("查询 Agent Definition 最大版本失败 agentId={} reason={}", agentId, error.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void saveVersion(AgentDefinitionSnapshot snapshot) {
        requireSnapshot(snapshot);
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        if (snapshot.currentPublished()) {
            template.update("UPDATE ai_ops_agent_definition_version SET current_published = 0 WHERE agent_id = ?",
                    snapshot.agentId());
        }
        upsertVersion(template, snapshot);
        if (snapshot.currentPublished()) {
            upsertCurrent(template, snapshot);
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentDefinitionPublishResult publish(AgentDefinitionSnapshot snapshot, String expectedVersionHash) {
        requireSnapshot(snapshot);
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        CurrentPointer current = currentPointer(template, snapshot.agentId()).orElse(null);
        if (current != null
                && current.enabled()
                && current.version() == snapshot.version()
                && current.definitionHash().equals(snapshot.definitionHash())) {
            return AgentDefinitionPublishResult.ALREADY_CURRENT;
        }

        int versionUpdated = template.update("""
                        UPDATE ai_ops_agent_definition_version
                        SET lifecycle='PUBLISHED', definition_hash=?, definition_json=?,
                            current_published=1, update_time=CURRENT_TIMESTAMP
                        WHERE agent_id=? AND version=? AND enabled=1
                          AND lifecycle IN ('VALIDATED','PUBLISHED') AND definition_hash=?
                        """,
                snapshot.definitionHash(),
                snapshot.definitionJson(),
                snapshot.agentId(),
                snapshot.version(),
                value(expectedVersionHash));
        if (versionUpdated != 1) {
            throw new AgentDefinitionPublishConflict(
                    AgentDefinitionPublishConflict.Reason.VERSION_CHANGED);
        }
        template.update("""
                        UPDATE ai_ops_agent_definition_version
                        SET current_published=0
                        WHERE agent_id=? AND version<>?
                        """,
                snapshot.agentId(),
                snapshot.version());

        int pointerUpdated;
        if (current == null) {
            pointerUpdated = insertCurrent(template, snapshot);
        } else {
            pointerUpdated = updateCurrentCas(template, snapshot, current);
        }
        if (pointerUpdated != 1) {
            throw new AgentDefinitionPublishConflict(
                    AgentDefinitionPublishConflict.Reason.CURRENT_POINTER_CHANGED);
        }
        return AgentDefinitionPublishResult.PUBLISHED;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean disableVersion(String agentId, int version) {
        if (!hasText(agentId) || version <= 0) {
            return false;
        }
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                        UPDATE ai_ops_agent_definition_version
                        SET enabled = 0, current_published = 0, lifecycle = 'DISABLED'
                        WHERE agent_id = ? AND version = ?
                        """,
                agentId.trim(), version);
        template.update("""
                        UPDATE ai_ops_agent_definition
                        SET enabled = 0, lifecycle = 'DISABLED'
                        WHERE agent_id = ? AND version = ?
                        """,
                agentId.trim(), version);
        return true;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void disableCurrent(String agentId) {
        if (!hasText(agentId)) {
            return;
        }
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("UPDATE ai_ops_agent_definition SET enabled = 0 WHERE agent_id = ?", agentId.trim());
        template.update("UPDATE ai_ops_agent_definition_version SET current_published = 0 WHERE agent_id = ?",
                agentId.trim());
    }

    private void upsertVersion(JdbcTemplate template, AgentDefinitionSnapshot snapshot) {
        template.update("""
                        INSERT INTO ai_ops_agent_definition_version
                        (agent_id, version, definition_hash, lifecycle, name, project_id, engine,
                         description, instruction, start_node_id, definition_json, enabled,
                         current_published, source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          definition_hash=VALUES(definition_hash),
                          lifecycle=VALUES(lifecycle),
                          name=VALUES(name),
                          project_id=VALUES(project_id),
                          engine=VALUES(engine),
                          description=VALUES(description),
                          instruction=VALUES(instruction),
                          start_node_id=VALUES(start_node_id),
                          definition_json=VALUES(definition_json),
                          enabled=VALUES(enabled),
                          current_published=VALUES(current_published),
                          source=VALUES(source)
                        """,
                snapshot.agentId(), snapshot.version(), snapshot.definitionHash(),
                snapshot.lifecycle().name(), snapshot.name(), snapshot.projectId(), snapshot.engine(),
                snapshot.description(), snapshot.instruction(), snapshot.startNodeId(), snapshot.definitionJson(),
                snapshot.enabled() ? 1 : 0, snapshot.currentPublished() ? 1 : 0, snapshot.source());
    }

    private void upsertCurrent(JdbcTemplate template, AgentDefinitionSnapshot snapshot) {
        template.update("""
                        INSERT INTO ai_ops_agent_definition
                        (agent_id, name, project_id, engine, description, instruction, start_node_id,
                         definition_json, enabled, version, definition_hash, lifecycle, source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          name=VALUES(name), project_id=VALUES(project_id), engine=VALUES(engine),
                          description=VALUES(description), instruction=VALUES(instruction),
                          start_node_id=VALUES(start_node_id), definition_json=VALUES(definition_json),
                          enabled=VALUES(enabled), version=VALUES(version),
                          definition_hash=VALUES(definition_hash), lifecycle=VALUES(lifecycle),
                          source=VALUES(source)
                        """,
                snapshot.agentId(), snapshot.name(), snapshot.projectId(), snapshot.engine(),
                snapshot.description(), snapshot.instruction(), snapshot.startNodeId(), snapshot.definitionJson(),
                snapshot.enabled() ? 1 : 0, snapshot.version(), snapshot.definitionHash(),
                snapshot.lifecycle().name(), snapshot.source());
    }

    private int insertCurrent(JdbcTemplate template, AgentDefinitionSnapshot snapshot) {
        return template.update("""
                        INSERT IGNORE INTO ai_ops_agent_definition
                          (agent_id, name, project_id, engine, description, instruction, start_node_id,
                           definition_json, enabled, version, definition_hash, lifecycle, source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'PUBLISHED', ?)
                        """,
                snapshot.agentId(), snapshot.name(), snapshot.projectId(), snapshot.engine(),
                snapshot.description(), snapshot.instruction(), snapshot.startNodeId(), snapshot.definitionJson(),
                snapshot.version(), snapshot.definitionHash(), snapshot.source());
    }

    private int updateCurrentCas(JdbcTemplate template,
                                 AgentDefinitionSnapshot snapshot,
                                 CurrentPointer current) {
        return template.update("""
                        UPDATE ai_ops_agent_definition
                        SET name=?, project_id=?, engine=?, description=?, instruction=?, start_node_id=?,
                            definition_json=?, version=?, definition_hash=?, lifecycle='PUBLISHED', source=?, enabled=1
                        WHERE agent_id=? AND version=? AND definition_hash=? AND enabled=?
                        """,
                snapshot.name(), snapshot.projectId(), snapshot.engine(), snapshot.description(),
                snapshot.instruction(), snapshot.startNodeId(), snapshot.definitionJson(), snapshot.version(),
                snapshot.definitionHash(), snapshot.source(), snapshot.agentId(), current.version(),
                current.definitionHash(), current.enabled() ? 1 : 0);
    }

    private Optional<CurrentPointer> currentPointer(JdbcTemplate template, String agentId) {
        List<Map<String, Object>> rows = template.queryForList("""
                SELECT version, definition_hash, enabled
                FROM ai_ops_agent_definition
                WHERE agent_id=?
                LIMIT 1
                """, agentId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new CurrentPointer(
                number(row.get("version")),
                value(row.get("definition_hash")),
                number(row.get("enabled")) != 0));
    }

    private AgentDefinitionSnapshot snapshot(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentDefinitionSnapshot(
                resultSet.getString("agent_id"),
                resultSet.getInt("version"),
                resultSet.getString("definition_hash"),
                AgentDefinitionLifecycle.require(resultSet.getString("lifecycle")),
                resultSet.getString("name"),
                resultSet.getString("project_id"),
                resultSet.getString("engine"),
                resultSet.getString("description"),
                resultSet.getString("instruction"),
                resultSet.getString("start_node_id"),
                resultSet.getString("definition_json"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("current_published"),
                resultSet.getString("source"));
    }

    private JdbcTemplate availableTemplate() {
        return jdbcEnabled ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            throw new IllegalStateException("AGENT_DEFINITION_STORE_UNAVAILABLE");
        }
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) {
            schemaInitializer.initialize();
        }
    }

    private void requireSnapshot(AgentDefinitionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_SNAPSHOT_REQUIRED");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }

    private int number(Object input) {
        if (input instanceof Number number) {
            return number.intValue();
        }
        try {
            return input == null ? 0 : Integer.parseInt(String.valueOf(input));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record CurrentPointer(int version, String definitionHash, boolean enabled) {
    }
}
