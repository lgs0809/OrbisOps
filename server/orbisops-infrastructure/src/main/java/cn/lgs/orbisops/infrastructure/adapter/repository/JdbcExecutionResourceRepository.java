package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcExecutionResourceRepository implements IExecutionResourceRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.execution-resource.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.execution-resource.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcExecutionResourceRepository(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void init() {
        JdbcTemplate template = availableTemplate();
        if (template != null) ensureTable(template);
    }

    @Override
    public List<ExecutionResource> findAllVisible() {
        JdbcTemplate template = availableTemplate();
        if (template == null) return List.of();
        ensureTable(template);
        return List.copyOf(template.query("""
                        SELECT resource_id, project_id, name, worker_id, adapter,
                               COALESCE(adapter_template_id, '') AS adapter_template_id,
                               environments_json, configuration_json, status,
                               create_time, update_time
                        FROM ai_ops_execution_resource
                        WHERE status <> 'DELETED'
                        ORDER BY project_id, resource_id
                        """,
                this::resource));
    }

    @Override
    public Optional<ExecutionResource> find(String projectId, String resourceId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) return Optional.empty();
        ensureTable(template);
        return template.query("""
                        SELECT resource_id, project_id, name, worker_id, adapter,
                               COALESCE(adapter_template_id, '') AS adapter_template_id,
                               environments_json, configuration_json, status,
                               create_time, update_time
                        FROM ai_ops_execution_resource
                        WHERE project_id = ? AND resource_id = ? AND status <> 'DELETED'
                        LIMIT 1
                        """,
                this::resource,
                projectId,
                resourceId).stream().findFirst();
    }

    @Override
    public boolean existsWorkerResourceOutsideProject(
            String workerId,
            String resourceId,
            String projectId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) return false;
        ensureTable(template);
        Long count = template.queryForObject("""
                        SELECT COUNT(1)
                        FROM ai_ops_execution_resource
                        WHERE worker_id = ? AND resource_id = ?
                          AND project_id <> ? AND status <> 'DELETED'
                        """,
                Long.class,
                workerId,
                resourceId,
                projectId);
        return count != null && count > 0;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public ExecutionResource save(ExecutionResource resource) {
        if (resource == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_REQUIRED");
        JdbcTemplate template = availableTemplate();
        if (template == null) return resource;
        ensureTable(template);
        try {
            int updated = updateExisting(template, resource);
            if (updated == 0) insert(template, resource);
        } catch (DuplicateKeyException error) {
            if (constraint(error, "uk_execution_resource")) {
                updateExisting(template, resource);
            } else {
                throw workerIdentityConflict(resource, error);
            }
        } catch (org.springframework.dao.DataIntegrityViolationException error) {
            throw new IllegalArgumentException(
                    "EXECUTION_RESOURCE_PERSISTENCE_CONSTRAINT:" + resource.resourceId(), error);
        }
        return find(resource.projectId(), resource.resourceId())
                .orElseThrow(() -> new IllegalStateException(
                        "EXECUTION_RESOURCE_SAVE_NOT_VISIBLE:" + resource.resourceId()));
    }

    private int updateExisting(JdbcTemplate template, ExecutionResource resource) {
        try {
            return template.update("""
                            UPDATE ai_ops_execution_resource
                            SET name = ?, worker_id = ?, adapter = ?, adapter_template_id = ?,
                                environments_json = ?, configuration_json = ?, status = ?, update_time = ?
                            WHERE project_id = ? AND resource_id = ?
                            """,
                    resource.name(),
                    resource.workerId(),
                    resource.adapter().code(),
                    resource.adapterTemplateId(),
                    JSON.toJSONString(resource.environments()),
                    JSON.toJSONString(resource.configuration()),
                    resource.status().name(),
                    Timestamp.valueOf(resource.updatedAt()),
                    resource.projectId(),
                    resource.resourceId());
        } catch (DuplicateKeyException error) {
            throw workerIdentityConflict(resource, error);
        }
    }

    private void insert(JdbcTemplate template, ExecutionResource resource) {
        template.update("""
                        INSERT INTO ai_ops_execution_resource
                        (resource_id, project_id, name, worker_id, adapter, adapter_template_id,
                         environments_json, configuration_json, status, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                resource.resourceId(),
                resource.projectId(),
                resource.name(),
                resource.workerId(),
                resource.adapter().code(),
                resource.adapterTemplateId(),
                JSON.toJSONString(resource.environments()),
                JSON.toJSONString(resource.configuration()),
                resource.status().name(),
                Timestamp.valueOf(resource.createdAt()),
                Timestamp.valueOf(resource.updatedAt()));
    }

    private boolean constraint(DuplicateKeyException error, String name) {
        Throwable cause = error.getMostSpecificCause();
        String message = cause == null ? error.getMessage() : cause.getMessage();
        return message != null && message.contains(name);
    }

    private IllegalArgumentException workerIdentityConflict(
            ExecutionResource resource,
            RuntimeException error) {
        return new IllegalArgumentException(
                "EXECUTION_RESOURCE_WORKER_IDENTITY_CONFLICT:"
                        + resource.workerId() + ":" + resource.resourceId(), error);
    }

    private ExecutionResource resource(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExecutionResource(
                resultSet.getString("resource_id"),
                resultSet.getString("project_id"),
                resultSet.getString("name"),
                resultSet.getString("worker_id"),
                ExecutionAdapterType.require(resultSet.getString("adapter")),
                resultSet.getString("adapter_template_id"),
                strings(resultSet.getString("environments_json")),
                map(resultSet.getString("configuration_json")),
                ExecutionResourceStatus.require(resultSet.getString("status")),
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private JdbcTemplate availableTemplate() {
        return jdbcEnabled ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized || !autoInit) return;
        synchronized (this) {
            if (initialized) return;
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_execution_resource (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      resource_id VARCHAR(120) NOT NULL,
                      project_id VARCHAR(80) NOT NULL,
                      name VARCHAR(128) NOT NULL,
                      worker_id VARCHAR(120) NOT NULL,
                      adapter VARCHAR(40) NOT NULL,
                      adapter_template_id VARCHAR(120) NOT NULL DEFAULT '',
                      environments_json TEXT NOT NULL,
                      configuration_json LONGTEXT NOT NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_execution_resource (project_id, resource_id),
                      UNIQUE KEY uk_worker_resource (worker_id, resource_id),
                      KEY idx_execution_worker (worker_id, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目变更执行资源'
                    """);
            ensureColumn(template, "ai_ops_execution_resource", "adapter_template_id",
                    "VARCHAR(120) NOT NULL DEFAULT '' COMMENT '来源执行适配器模板ID' AFTER adapter");
            initialized = true;
        }
    }

    private void ensureColumn(
            JdbcTemplate template,
            String table,
            String column,
            String definition) {
        try {
            Long count = template.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Long.class, table, column);
            if (count == null || count == 0L) {
                template.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        } catch (RuntimeException ignored) {
            // Preserve limited-database-user and test-context compatibility.
        }
    }

    private List<String> strings(String json) {
        if (json == null || json.isBlank()) return List.of();
        JSONArray array = JSON.parseArray(json);
        if (array == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : array) result.add(String.valueOf(value));
        return List.copyOf(result);
    }

    private Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) return Map.of();
        JSONObject object = JSON.parseObject(json);
        return object == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(object));
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? LocalDateTime.now() : value.toLocalDateTime();
    }
}
