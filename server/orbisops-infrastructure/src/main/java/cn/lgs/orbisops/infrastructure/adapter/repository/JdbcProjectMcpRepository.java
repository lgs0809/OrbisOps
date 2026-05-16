package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.project.ProjectMcpSnapshotPort;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Repository
public class JdbcProjectMcpRepository implements
        IProjectMcpRepository,
        ProjectMcpSnapshotPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ConcurrentMap<String, ProjectMcpDefinition> memory =
            new ConcurrentHashMap<>();

    @Value("${orbisops.project-workspace.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.project-workspace.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcProjectMcpRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<ProjectMcpDefinition> listAll() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return memoryValues("", "");
        }
        try {
            ensureTable(template);
            List<ProjectMcpDefinition> definitions = template.query(selectSql("", ""), this::definition);
            definitions.forEach(this::remember);
            return List.copyOf(definitions);
        } catch (DataAccessException error) {
            log.warn("加载全部项目 MCP 失败 reason={}", error.getMessage());
            return memoryValues("", "");
        }
    }

    @Override
    public List<ProjectMcpDefinition> list(String projectId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return memoryValues(projectId, "");
        }
        try {
            ensureTable(template);
            List<ProjectMcpDefinition> definitions = template.query(
                    selectSql("WHERE project_id = ? AND enabled = 1", "ORDER BY id DESC"),
                    this::definition,
                    projectId);
            definitions.forEach(this::remember);
            return List.copyOf(definitions);
        } catch (DataAccessException error) {
            log.warn("加载项目 MCP 失败 projectId={} reason={}", projectId, error.getMessage());
            return memoryValues(projectId, "");
        }
    }

    @Override
    public List<ProjectMcpDefinition> listByTemplate(String templateId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return memoryValues("", templateId);
        }
        try {
            ensureTable(template);
            List<ProjectMcpDefinition> definitions = template.query(
                    selectSql("WHERE template_id = ? AND enabled = 1", "ORDER BY id DESC"),
                    this::definition,
                    templateId);
            definitions.forEach(this::remember);
            return List.copyOf(definitions);
        } catch (DataAccessException error) {
            log.warn("按模板加载项目 MCP 失败 templateId={} reason={}", templateId, error.getMessage());
            return memoryValues("", templateId);
        }
    }

    @Override
    public Optional<ProjectMcpDefinition> find(String projectId, String mcpId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return Optional.ofNullable(memory.get(key(projectId, mcpId)));
        }
        try {
            ensureTable(template);
            List<ProjectMcpDefinition> definitions = template.query(
                    selectSql("WHERE project_id = ? AND mcp_id = ? AND enabled = 1", "LIMIT 1"),
                    this::definition,
                    projectId,
                    mcpId);
            Optional<ProjectMcpDefinition> result = definitions.stream().findFirst();
            result.ifPresent(this::remember);
            return result.or(() -> Optional.ofNullable(memory.get(key(projectId, mcpId))));
        } catch (DataAccessException error) {
            log.warn("查询项目 MCP 失败 mcpId={} reason={}", mcpId, error.getMessage());
            return Optional.ofNullable(memory.get(key(projectId, mcpId)));
        }
    }

    @Override
    public ProjectMcpDefinition save(ProjectMcpDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("PROJECT_MCP_REQUIRED");
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            remember(definition);
            return definition;
        }
        try {
            ensureTable(template);
            template.update("""
                            INSERT INTO ai_ops_project_mcp
                            (mcp_id, mcp_name, project_id, resource_id, resource_type,
                             transport_type, template_id, transport_config_json,
                             allowed_actions_json, risk_level, read_only,
                             permission_policy_json, request_timeout, status, enabled,
                             create_time, update_time)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              mcp_name=VALUES(mcp_name),
                              project_id=VALUES(project_id),
                              resource_id=VALUES(resource_id),
                              resource_type=VALUES(resource_type),
                              transport_type=VALUES(transport_type),
                              template_id=VALUES(template_id),
                              transport_config_json=VALUES(transport_config_json),
                              allowed_actions_json=VALUES(allowed_actions_json),
                              risk_level=VALUES(risk_level),
                              read_only=VALUES(read_only),
                              permission_policy_json=VALUES(permission_policy_json),
                              request_timeout=VALUES(request_timeout),
                              status=VALUES(status),
                              enabled=VALUES(enabled),
                              update_time=VALUES(update_time)
                            """,
                    definition.mcpId(),
                    definition.mcpName(),
                    definition.projectId(),
                    definition.resourceId(),
                    definition.resourceType(),
                    definition.transportType(),
                    definition.templateId(),
                    JSON.toJSONString(definition.transportConfig()),
                    JSON.toJSONString(definition.allowedActions()),
                    definition.riskLevel().name(),
                    definition.readOnly() ? 1 : 0,
                    JSON.toJSONString(definition.permissionPolicy()),
                    definition.requestTimeout(),
                    definition.status().name(),
                    timestamp(definition.createdAt()),
                    timestamp(definition.updatedAt()));
            remember(definition);
            return find(definition.projectId(), definition.mcpId()).orElse(definition);
        } catch (DataAccessException error) {
            log.warn("保存项目 MCP 失败 mcpId={}", definition.mcpId(), error);
            throw new IllegalStateException("保存项目 MCP 失败：" + definition.mcpId(), error);
        }
    }

    private ProjectMcpDefinition definition(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProjectMcpDefinition(
                resultSet.getString("mcp_id"),
                resultSet.getString("mcp_name"),
                resultSet.getString("project_id"),
                resultSet.getString("resource_id"),
                resultSet.getString("resource_type"),
                resultSet.getString("transport_type"),
                resultSet.getString("template_id"),
                map(resultSet.getString("transport_config_json")),
                strings(resultSet.getString("allowed_actions_json")),
                ProjectMcpRiskLevel.failClosed(resultSet.getString("risk_level")),
                resultSet.getBoolean("read_only"),
                map(resultSet.getString("permission_policy_json")),
                resultSet.getInt("request_timeout"),
                ProjectMcpStatus.from(resultSet.getString("status")),
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private String selectSql(String where, String order) {
        return """
                SELECT mcp_id, mcp_name, project_id, resource_id, resource_type,
                       transport_type, template_id, transport_config_json,
                       allowed_actions_json, risk_level, read_only,
                       permission_policy_json, request_timeout, status,
                       create_time, update_time
                FROM ai_ops_project_mcp
                """ + (where == null || where.isBlank() ? "WHERE enabled = 1\n" : where + "\n")
                + (order == null || order.isBlank() ? "ORDER BY id DESC" : order);
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
                    CREATE TABLE IF NOT EXISTS ai_ops_project_mcp (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      mcp_id VARCHAR(128) NOT NULL COMMENT '项目级MCP ID',
                      mcp_name VARCHAR(160) NOT NULL COMMENT 'MCP名称',
                      project_id VARCHAR(80) NOT NULL COMMENT '业务系统ID',
                      resource_id VARCHAR(128) NOT NULL COMMENT '资源ID',
                      resource_type VARCHAR(48) NOT NULL COMMENT '资源类型',
                      transport_type VARCHAR(32) NOT NULL DEFAULT 'stdio' COMMENT '传输类型',
                      template_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源MCP模板ID',
                      transport_config_json MEDIUMTEXT NULL COMMENT '脱敏传输配置JSON',
                      allowed_actions_json TEXT NULL COMMENT '允许动作JSON',
                      risk_level VARCHAR(24) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
                      read_only TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否只读',
                      permission_policy_json MEDIUMTEXT NULL COMMENT '权限策略JSON',
                      request_timeout INT NOT NULL DEFAULT 30 COMMENT '超时秒',
                      status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                      enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_mcp_id (mcp_id),
                      KEY idx_project_resource (project_id, resource_id),
                      KEY idx_resource_type (resource_type),
                      KEY idx_enabled (enabled)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维项目级MCP表'
                    """);
            addColumnIfMissing(template, "template_id",
                    "VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源MCP模板ID'");
            addColumnIfMissing(template, "allowed_actions_json",
                    "TEXT NULL COMMENT '允许动作JSON'");
            addColumnIfMissing(template, "risk_level",
                    "VARCHAR(24) NOT NULL DEFAULT 'LOW' COMMENT '风险等级'");
            addColumnIfMissing(template, "read_only",
                    "TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否只读'");
            addColumnIfMissing(template, "permission_policy_json",
                    "MEDIUMTEXT NULL COMMENT '权限策略JSON'");
            initialized = true;
        }
    }

    private void addColumnIfMissing(JdbcTemplate template, String columnName, String definition) {
        try {
            Integer count = template.queryForObject("""
                            SELECT COUNT(*)
                            FROM information_schema.COLUMNS
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME = 'ai_ops_project_mcp'
                              AND COLUMN_NAME = ?
                            """,
                    Integer.class,
                    columnName);
            if (count == null || count == 0) {
                template.execute("ALTER TABLE ai_ops_project_mcp ADD COLUMN "
                        + columnName + " " + definition);
            }
        } catch (DataAccessException error) {
            log.warn("补齐项目 MCP 字段失败 column={} reason={}", columnName, error.getMessage());
        }
    }

    private void remember(ProjectMcpDefinition definition) {
        memory.put(key(definition.projectId(), definition.mcpId()), definition);
    }

    private List<ProjectMcpDefinition> memoryValues(String projectId, String templateId) {
        return memory.values().stream()
                .filter(definition -> projectId == null || projectId.isBlank()
                        || projectId.equals(definition.projectId()))
                .filter(definition -> templateId == null || templateId.isBlank()
                        || templateId.equals(definition.templateId()))
                .sorted(Comparator.comparing(
                        ProjectMcpDefinition::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String key(String projectId, String mcpId) {
        return value(projectId) + "::" + value(mcpId);
    }

    private Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null ? Map.of() : new LinkedHashMap<>(object);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            if (array == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            array.forEach(item -> {
                String normalized = item == null ? "" : String.valueOf(item).trim();
                if (!normalized.isBlank()) {
                    values.add(normalized);
                }
            });
            return values.stream().distinct().toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
