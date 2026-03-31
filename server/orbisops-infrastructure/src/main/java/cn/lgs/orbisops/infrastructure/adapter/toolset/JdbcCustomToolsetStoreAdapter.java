package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.toolset.CustomToolDefinitionRecord;
import cn.lgs.orbisops.application.toolset.CustomToolsetRecord;
import cn.lgs.orbisops.application.toolset.CustomToolsetStorePort;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JDBC custom Toolset store with bounded in-memory fallback when MySQL is absent. */
@Slf4j
@Repository
public class JdbcCustomToolsetStoreAdapter implements CustomToolsetStorePort {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CustomToolsetRecord> memory =
            new ConcurrentHashMap<>();

    @Value("${orbisops.toolset.auto-init:true}")
    private boolean autoInit;

    public JdbcCustomToolsetStoreAdapter(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        ensureTables();
    }

    @Override
    public List<CustomToolsetRecord> list(String projectId) {
        String project = text(projectId);
        if (jdbcTemplate == null) {
            String prefix = project + ":";
            return memory.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .toList();
        }
        try {
            return jdbcTemplate.queryForList("""
                            SELECT toolset_id, project_id, name, description, prerequisites, tags_json, adapter_type,
                                   enabled, read_only_default, tools_json, create_by, update_by, create_time, update_time
                            FROM ai_ops_toolset
                            WHERE project_id = ? AND source_type = 'CUSTOM'
                            ORDER BY update_time DESC, id DESC
                            """,
                    project).stream().map(this::fromRow).toList();
        } catch (DataAccessException error) {
            log.warn(
                    "查询自定义 Toolset 失败 projectId={} reason={}",
                    project,
                    error.getMessage());
            return List.of();
        }
    }

    @Override
    public CustomToolsetRecord upsert(CustomToolsetRecord record) {
        memory.put(key(record.projectId(), record.toolsetId()), record);
        if (jdbcTemplate == null) return record;
        ensureTables();
        jdbcTemplate.update("""
                        INSERT INTO ai_ops_toolset
                        (toolset_id, project_id, name, description, prerequisites, tags_json, source_type, adapter_type,
                         enabled, read_only_default, tools_json, create_by, update_by)
                        VALUES (?, ?, ?, ?, ?, ?, 'CUSTOM', ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description),
                          prerequisites=VALUES(prerequisites), tags_json=VALUES(tags_json), adapter_type=VALUES(adapter_type),
                          enabled=VALUES(enabled), read_only_default=VALUES(read_only_default), tools_json=VALUES(tools_json),
                          update_by=VALUES(update_by), update_time=CURRENT_TIMESTAMP
                        """,
                record.toolsetId(),
                record.projectId(),
                record.name(),
                record.description(),
                record.prerequisites(),
                JSON.toJSONString(record.tags()),
                record.adapterType(),
                record.enabled() ? 1 : 0,
                record.readOnlyDefault() ? 1 : 0,
                JSON.toJSONString(record.tools().stream()
                        .map(this::toolView)
                        .toList()),
                record.createBy(),
                record.updateBy());
        return record;
    }

    @Override
    public void setEnabled(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor) {
        memory.computeIfPresent(
                key(projectId, toolsetId),
                (ignored, record) -> record.withEnabled(enabled, actor));
        if (jdbcTemplate == null) return;
        ensureTables();
        jdbcTemplate.update("""
                        UPDATE ai_ops_toolset
                        SET enabled=?, update_by=?, update_time=CURRENT_TIMESTAMP
                        WHERE project_id=? AND toolset_id=?
                        """,
                enabled ? 1 : 0,
                text(actor),
                text(projectId),
                text(toolsetId));
    }

    private void ensureTables() {
        if (!autoInit || jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_toolset (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      toolset_id VARCHAR(128) NOT NULL,
                      project_id VARCHAR(128) NOT NULL,
                      name VARCHAR(256) NOT NULL DEFAULT '',
                      description TEXT NULL,
                      prerequisites TEXT NULL,
                      tags_json TEXT NULL,
                      source_type VARCHAR(32) NOT NULL,
                      adapter_type VARCHAR(48) NOT NULL,
                      enabled TINYINT NOT NULL DEFAULT 1,
                      read_only_default TINYINT NOT NULL DEFAULT 1,
                      tools_json MEDIUMTEXT NULL,
                      create_by VARCHAR(128) NOT NULL DEFAULT '',
                      update_by VARCHAR(128) NOT NULL DEFAULT '',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_toolset_project (project_id, toolset_id),
                      KEY idx_toolset_project_update (project_id, update_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维 Toolset 定义'
                    """);
        } catch (DataAccessException error) {
            log.warn("初始化 Toolset 表失败：{}", error.getMessage());
        }
    }

    private CustomToolsetRecord fromRow(Map<String, Object> row) {
        return new CustomToolsetRecord(
                text(row.get("project_id")),
                text(row.get("toolset_id")),
                text(row.get("name")),
                text(row.get("description")),
                text(row.get("prerequisites")),
                strings(row.get("tags_json")),
                "CUSTOM",
                text(row.get("adapter_type")),
                bool(row.get("enabled"), true),
                bool(row.get("read_only_default"), true),
                tools(row.get("tools_json")),
                text(row.get("create_by")),
                text(row.get("update_by")),
                time(row.get("create_time")),
                time(row.get("update_time")));
    }

    private List<CustomToolDefinitionRecord> tools(Object value) {
        String json = text(value);
        if (json.isBlank()) return List.of();
        try {
            JSONArray array = JSON.parseArray(json);
            if (array == null) return List.of();
            List<CustomToolDefinitionRecord> result = new ArrayList<>();
            for (Object item : array) {
                if (item instanceof Map<?, ?> map) {
                    result.add(toolFromMap(map));
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException error) {
            log.warn("解析 Toolset tools_json 失败：{}", error.getMessage());
            return List.of();
        }
    }

    private CustomToolDefinitionRecord toolFromMap(Map<?, ?> source) {
        Map<String, Object> map = new LinkedHashMap<>();
        source.forEach((key, value) -> map.put(String.valueOf(key), value));
        return new CustomToolDefinitionRecord(
                text(map.get("toolName")),
                text(map.get("displayName")),
                text(map.get("description")),
                json(map.get("parametersJson")),
                text(map.get("adapterType")),
                text(map.get("commandTemplate")),
                text(map.get("mcpServerId")),
                text(map.get("remoteToolName")),
                json(map.get("httpConfigJson")),
                json(map.get("dbConfigJson")),
                bool(map.get("readOnly"), true),
                bool(map.get("writesRepairWorkspace"), false),
                bool(map.get("writesTargetResource"), false),
                bool(map.get("requiresChangePackage"), false),
                bool(map.get("requiresApproval"), false),
                text(map.get("riskLevel")),
                json(map.get("outputBudgetJson")),
                bool(map.get("enabled"), true));
    }

    private Map<String, Object> toolView(CustomToolDefinitionRecord tool) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("toolName", tool.toolName());
        map.put("displayName", tool.displayName());
        map.put("description", tool.description());
        map.put("parametersJson", tool.parametersJson());
        map.put("adapterType", tool.adapterType());
        map.put("commandTemplate", tool.commandTemplate());
        map.put("mcpServerId", tool.mcpServerId());
        map.put("remoteToolName", tool.remoteToolName());
        map.put("httpConfigJson", tool.httpConfigJson());
        map.put("dbConfigJson", tool.dbConfigJson());
        map.put("readOnly", tool.readOnly());
        map.put("writesRepairWorkspace", tool.writesRepairWorkspace());
        map.put("writesTargetResource", tool.writesTargetResource());
        map.put("requiresChangePackage", tool.requiresChangePackage());
        map.put("requiresApproval", tool.requiresApproval());
        map.put("riskLevel", tool.riskLevel());
        map.put("outputBudgetJson", tool.outputBudgetJson());
        map.put("enabled", tool.enabled());
        return map;
    }

    private List<String> strings(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            iterable.forEach(item -> result.add(text(item)));
            return List.copyOf(result);
        }
        String json = text(value);
        if (json.isBlank()) return List.of();
        try {
            if (json.startsWith("[")) {
                List<String> result = JSON.parseObject(
                        json,
                        new TypeReference<List<String>>() {
                        });
                return result == null ? List.of() : List.copyOf(result);
            }
            return List.of(json.split(","));
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = text(value);
        return text.isBlank()
                ? fallback
                : Boolean.parseBoolean(text) || "1".equals(text);
    }

    private String json(Object value) {
        String text = text(value);
        if (!text.isBlank() && (text.startsWith("{") || text.startsWith("["))) {
            return text;
        }
        return JSON.toJSONString(value == null ? Map.of() : value);
    }

    private String time(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        return text(value);
    }

    private String key(String projectId, String toolsetId) {
        return text(projectId) + ":" + text(toolsetId);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
