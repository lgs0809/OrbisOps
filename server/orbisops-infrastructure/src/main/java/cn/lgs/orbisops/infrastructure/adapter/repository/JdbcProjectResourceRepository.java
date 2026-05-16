package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.project.ProjectResourceSnapshotPort;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import com.alibaba.fastjson.JSON;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Repository
public class JdbcProjectResourceRepository implements
        IProjectResourceRepository,
        ProjectResourceSnapshotPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ConcurrentMap<String, ProjectResourceDefinition> memory =
            new ConcurrentHashMap<>();

    @Value("${orbisops.project-workspace.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.project-workspace.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcProjectResourceRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<ProjectResourceDefinition> list(String projectId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return memoryValues(projectId);
        }
        try {
            ensureTable(template);
            List<ProjectResourceDefinition> resources = template.query("""
                            SELECT resource_id, project_id, resource_type, type_name, name,
                                   environment, endpoint, credential_json, status, schema_json,
                                   permission_json, create_time, update_time
                            FROM ai_ops_project_resource
                            WHERE project_id = ? AND enabled = 1
                            ORDER BY id ASC
                            """,
                    this::resource,
                    projectId);
            resources.forEach(this::remember);
            return List.copyOf(resources);
        } catch (DataAccessException error) {
            log.warn("加载项目资源失败 projectId={} reason={}", projectId, error.getMessage());
            return memoryValues(projectId);
        }
    }

    @Override
    public List<ProjectResourceDefinition> listAll() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return memoryValues("");
        }
        try {
            ensureTable(template);
            List<ProjectResourceDefinition> resources = template.query("""
                            SELECT resource_id, project_id, resource_type, type_name, name,
                                   environment, endpoint, credential_json, status, schema_json,
                                   permission_json, create_time, update_time
                            FROM ai_ops_project_resource
                            WHERE enabled = 1
                            ORDER BY id ASC
                            """,
                    this::resource);
            resources.forEach(this::remember);
            return List.copyOf(resources);
        } catch (DataAccessException error) {
            log.warn("加载全部项目资源失败 reason={}", error.getMessage());
            return memoryValues("");
        }
    }

    @Override
    public Optional<ProjectResourceDefinition> find(String projectId, String resourceId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return Optional.ofNullable(memory.get(key(projectId, resourceId)));
        }
        try {
            ensureTable(template);
            List<ProjectResourceDefinition> resources = template.query("""
                            SELECT resource_id, project_id, resource_type, type_name, name,
                                   environment, endpoint, credential_json, status, schema_json,
                                   permission_json, create_time, update_time
                            FROM ai_ops_project_resource
                            WHERE project_id = ? AND resource_id = ? AND enabled = 1
                            LIMIT 1
                            """,
                    this::resource,
                    projectId,
                    resourceId);
            Optional<ProjectResourceDefinition> result = resources.stream().findFirst();
            result.ifPresent(this::remember);
            return result.or(() -> Optional.ofNullable(memory.get(key(projectId, resourceId))));
        } catch (DataAccessException error) {
            log.warn("查询项目资源失败 resourceId={} reason={}", resourceId, error.getMessage());
            return Optional.ofNullable(memory.get(key(projectId, resourceId)));
        }
    }

    @Override
    public ProjectResourceDefinition save(ProjectResourceDefinition resource) {
        if (resource == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_REQUIRED");
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            remember(resource);
            return resource;
        }
        try {
            ensureTable(template);
            template.update("""
                            INSERT INTO ai_ops_project_resource
                            (resource_id, project_id, resource_type, type_name, name, environment,
                             endpoint, credential_json, status, schema_json, permission_json,
                             enabled, create_time, update_time)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              project_id=VALUES(project_id),
                              resource_type=VALUES(resource_type),
                              type_name=VALUES(type_name),
                              name=VALUES(name),
                              environment=VALUES(environment),
                              endpoint=VALUES(endpoint),
                              credential_json=VALUES(credential_json),
                              status=VALUES(status),
                              schema_json=VALUES(schema_json),
                              permission_json=VALUES(permission_json),
                              enabled=VALUES(enabled),
                              update_time=VALUES(update_time)
                            """,
                    resource.resourceId(),
                    resource.projectId(),
                    resource.type().value(),
                    resource.typeName(),
                    resource.name(),
                    resource.environment(),
                    resource.endpoint(),
                    JSON.toJSONString(resource.credential()),
                    resource.status(),
                    JSON.toJSONString(resource.schema()),
                    JSON.toJSONString(resource.permission()),
                    timestamp(resource.createdAt()),
                    timestamp(resource.updatedAt()));
            remember(resource);
            return find(resource.projectId(), resource.resourceId()).orElse(resource);
        } catch (DataAccessException error) {
            log.warn("保存项目资源失败 resourceId={}", resource.resourceId(), error);
            throw new IllegalStateException("保存数据连接失败：" + resource.resourceId(), error);
        }
    }

    private ProjectResourceDefinition resource(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProjectResourceDefinition(
                resultSet.getString("resource_id"),
                resultSet.getString("project_id"),
                ProjectResourceType.from(resultSet.getString("resource_type")),
                resultSet.getString("type_name"),
                resultSet.getString("name"),
                resultSet.getString("environment"),
                resultSet.getString("endpoint"),
                map(resultSet.getString("credential_json")),
                resultSet.getString("status"),
                map(resultSet.getString("schema_json")),
                map(resultSet.getString("permission_json")),
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
                    CREATE TABLE IF NOT EXISTS ai_ops_project_resource (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      resource_id VARCHAR(128) NOT NULL COMMENT '资源ID',
                      project_id VARCHAR(80) NOT NULL COMMENT '业务系统ID',
                      resource_type VARCHAR(48) NOT NULL COMMENT '资源类型',
                      type_name VARCHAR(80) NULL COMMENT '资源类型名称',
                      name VARCHAR(128) NOT NULL COMMENT '资源名称',
                      environment VARCHAR(32) NOT NULL DEFAULT 'prod' COMMENT '环境',
                      endpoint VARCHAR(512) NOT NULL COMMENT '连接地址',
                      credential_json TEXT NULL COMMENT '凭据JSON',
                      status VARCHAR(32) NOT NULL DEFAULT 'PREVIEW' COMMENT '扫描状态',
                      schema_json MEDIUMTEXT NULL COMMENT '对象结构JSON',
                      permission_json MEDIUMTEXT NULL COMMENT '权限策略JSON',
                      enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_resource_id (resource_id),
                      KEY idx_project_type (project_id, resource_type),
                      KEY idx_enabled (enabled)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维业务系统资源表'
                    """);
            initialized = true;
        }
    }

    private void remember(ProjectResourceDefinition resource) {
        memory.put(key(resource.projectId(), resource.resourceId()), resource);
    }

    private List<ProjectResourceDefinition> memoryValues(String projectId) {
        return memory.values().stream()
                .filter(resource -> projectId == null || projectId.isBlank()
                        || projectId.equals(resource.projectId()))
                .sorted(Comparator.comparing(ProjectResourceDefinition::projectId)
                        .thenComparing(ProjectResourceDefinition::resourceId))
                .toList();
    }

    private String key(String projectId, String resourceId) {
        return value(projectId) + "::" + value(resourceId);
    }

    private Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject value = JSON.parseObject(json);
            return value == null ? Map.of() : new LinkedHashMap<>(value);
        } catch (RuntimeException ignored) {
            return Map.of();
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
