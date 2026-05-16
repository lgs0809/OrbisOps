package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.project.ProjectDefinitionSnapshotPort;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Repository
public class JdbcProjectDefinitionRepository implements
        IProjectDefinitionRepository,
        ProjectDefinitionSnapshotPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ConcurrentMap<String, ProjectDefinition> memory = new ConcurrentHashMap<>();

    @Value("${orbisops.project-workspace.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.project-workspace.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcProjectDefinitionRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<ProjectDefinition> listEnabled() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return memoryValues();
        }
        try {
            ensureTable(template);
            List<ProjectDefinition> definitions = template.query("""
                            SELECT project_id, name, description, owner, environments_json,
                                   knowledge_base_id, default_agent_id, skill_ids_json,
                                   shared_mcp_ids_json, enabled, create_time, update_time
                            FROM ai_ops_project
                            WHERE enabled = 1
                            ORDER BY id ASC
                            """,
                    this::definition);
            definitions.forEach(definition -> memory.put(definition.projectId(), definition));
            return List.copyOf(definitions);
        } catch (DataAccessException error) {
            log.warn("加载项目定义失败 reason={}", error.getMessage());
            return memoryValues();
        }
    }

    @Override
    public Optional<ProjectDefinition> find(String projectId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return Optional.ofNullable(memory.get(projectId));
        }
        try {
            ensureTable(template);
            List<ProjectDefinition> definitions = template.query("""
                            SELECT project_id, name, description, owner, environments_json,
                                   knowledge_base_id, default_agent_id, skill_ids_json,
                                   shared_mcp_ids_json, enabled, create_time, update_time
                            FROM ai_ops_project
                            WHERE project_id = ? AND enabled = 1
                            LIMIT 1
                            """,
                    this::definition,
                    projectId);
            Optional<ProjectDefinition> result = definitions.stream().findFirst();
            result.ifPresent(definition -> memory.put(definition.projectId(), definition));
            return result.or(() -> Optional.ofNullable(memory.get(projectId)));
        } catch (DataAccessException error) {
            log.warn("查询项目定义失败 projectId={} reason={}", projectId, error.getMessage());
            return Optional.ofNullable(memory.get(projectId));
        }
    }

    @Override
    public boolean exists(String projectId) {
        return find(projectId).isPresent();
    }

    @Override
    public ProjectDefinition save(ProjectDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_REQUIRED");
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            memory.put(definition.projectId(), definition);
            return definition;
        }
        try {
            ensureTable(template);
            template.update("""
                            INSERT INTO ai_ops_project
                            (project_id, name, description, owner, environments_json,
                             knowledge_base_id, default_agent_id, skill_ids_json,
                             shared_mcp_ids_json, enabled, create_time, update_time)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              name=VALUES(name),
                              description=VALUES(description),
                              owner=VALUES(owner),
                              environments_json=VALUES(environments_json),
                              knowledge_base_id=VALUES(knowledge_base_id),
                              default_agent_id=VALUES(default_agent_id),
                              skill_ids_json=VALUES(skill_ids_json),
                              shared_mcp_ids_json=VALUES(shared_mcp_ids_json),
                              enabled=VALUES(enabled),
                              update_time=VALUES(update_time)
                            """,
                    definition.projectId(),
                    definition.name(),
                    definition.description(),
                    definition.owner(),
                    JSON.toJSONString(definition.environments()),
                    definition.knowledgeBaseId(),
                    definition.defaultAgentId(),
                    JSON.toJSONString(definition.skillIds()),
                    JSON.toJSONString(definition.sharedMcpIds()),
                    definition.enabled() ? 1 : 0,
                    timestamp(definition.createdAt()),
                    timestamp(definition.updatedAt()));
            memory.put(definition.projectId(), definition);
            return find(definition.projectId()).orElse(definition);
        } catch (DataAccessException error) {
            log.warn("保存项目定义失败 projectId={}", definition.projectId(), error);
            throw new IllegalStateException("保存项目失败：" + definition.projectId(), error);
        }
    }

    private ProjectDefinition definition(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProjectDefinition(
                resultSet.getString("project_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("owner"),
                strings(resultSet.getString("environments_json")),
                resultSet.getString("knowledge_base_id"),
                resultSet.getString("default_agent_id"),
                strings(resultSet.getString("skill_ids_json")),
                strings(resultSet.getString("shared_mcp_ids_json")),
                resultSet.getBoolean("enabled"),
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private JdbcTemplate availableTemplate() {
        return jdbcEnabled ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized || !autoInit) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_project (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      project_id VARCHAR(80) NOT NULL COMMENT '业务系统ID',
                      name VARCHAR(128) NOT NULL COMMENT '业务系统名称',
                      description TEXT NULL COMMENT '说明',
                      owner VARCHAR(80) NULL COMMENT '负责人',
                      environments_json TEXT NULL COMMENT '环境列表JSON',
                      knowledge_base_id VARCHAR(128) NULL COMMENT '默认知识库ID',
                      default_agent_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目默认Agent',
                      skill_ids_json TEXT NULL COMMENT '项目允许使用的Skill ID列表',
                      shared_mcp_ids_json TEXT NULL COMMENT '项目绑定的通用MCP ID列表',
                      enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_project_id (project_id),
                      KEY idx_enabled (enabled)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维业务系统空间表'
                    """);
            addColumnIfMissing(template, "default_agent_id",
                    "VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目默认Agent'");
            addColumnIfMissing(template, "skill_ids_json",
                    "TEXT NULL COMMENT '项目允许使用的Skill ID列表'");
            addColumnIfMissing(template, "shared_mcp_ids_json",
                    "TEXT NULL COMMENT '项目绑定的通用MCP ID列表'");
            initialized = true;
        }
    }

    private void addColumnIfMissing(JdbcTemplate template,
                                    String columnName,
                                    String definition) {
        Integer count = template.queryForObject("""
                        SELECT COUNT(1)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'ai_ops_project'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            template.execute("ALTER TABLE ai_ops_project ADD COLUMN "
                    + columnName + " " + definition);
        }
    }

    private List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = JSON.parseArray(json, String.class);
            return values == null ? List.of() : values.stream()
                    .filter(value -> value != null && !value.trim().isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<ProjectDefinition> memoryValues() {
        return memory.values().stream()
                .filter(ProjectDefinition::enabled)
                .sorted(Comparator.comparing(ProjectDefinition::projectId))
                .toList();
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
