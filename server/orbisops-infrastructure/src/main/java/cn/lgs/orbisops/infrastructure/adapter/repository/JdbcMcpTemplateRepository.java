package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpTemplateRepository;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.mcp.service.McpTemplateDefaults;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
public class JdbcMcpTemplateRepository implements IMcpTemplateRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.mcp-template.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcMcpTemplateRepository(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void init() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template != null) {
            ensureTable(template);
        }
    }

    @Override
    public List<McpTemplateCatalogEntry> listVisible() {
        JdbcTemplate template = requireTemplate();
        return List.copyOf(template.query("""
                        SELECT id, template_id, template_name, resource_type, transport_type,
                               default_transport_config_json, supported_actions_json, risk_level,
                               read_only, description, status, create_by, create_time, update_time
                        FROM ai_ops_mcp_template
                        WHERE status <> 'DELETED'
                          AND resource_type IN ('mysql', 'postgresql', 'redis',
                                                'elasticsearch', 'prometheus', 'rabbitmq')
                        ORDER BY FIELD(resource_type, 'mysql', 'postgresql', 'redis',
                                      'elasticsearch', 'prometheus', 'rabbitmq'), id ASC
                        """,
                this::entry));
    }

    @Override
    public Optional<McpTemplateCatalogEntry> findVisible(String templateId) {
        JdbcTemplate template = requireTemplate();
        List<McpTemplateCatalogEntry> entries = template.query("""
                        SELECT id, template_id, template_name, resource_type, transport_type,
                               default_transport_config_json, supported_actions_json, risk_level,
                               read_only, description, status, create_by, create_time, update_time
                        FROM ai_ops_mcp_template
                        WHERE template_id = ?
                          AND status <> 'DELETED'
                          AND resource_type IN ('mysql', 'postgresql', 'redis',
                                                'elasticsearch', 'prometheus', 'rabbitmq')
                        LIMIT 1
                        """,
                this::entry,
                templateId);
        return entries.stream().findFirst();
    }

    @Override
    public boolean exists(String templateId) {
        Long count = requireTemplate().queryForObject("""
                        SELECT COUNT(1)
                        FROM ai_ops_mcp_template
                        WHERE template_id = ? AND status <> 'DELETED'
                        """,
                Long.class,
                templateId);
        return count != null && count > 0;
    }

    @Override
    public McpTemplateCatalogEntry insert(McpTemplateDefinition definition) {
        requireDefinition(definition);
        JdbcTemplate template = requireTemplate();
        template.update("""
                        INSERT INTO ai_ops_mcp_template
                        (template_id, template_name, resource_type, transport_type,
                         default_transport_config_json, supported_actions_json,
                         risk_level, read_only, description, status, create_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                definition.templateId(),
                definition.templateName(),
                definition.resourceType(),
                definition.transportType(),
                JSON.toJSONString(definition.defaultTransportConfig()),
                JSON.toJSONString(definition.supportedActions()),
                definition.riskLevel(),
                definition.readOnly() ? 1 : 0,
                definition.description(),
                definition.status().name(),
                definition.createBy());
        return findVisible(definition.templateId()).orElseThrow(() ->
                new IllegalStateException("MCP_TEMPLATE_INSERT_NOT_VISIBLE:" + definition.templateId()));
    }

    @Override
    public McpTemplateCatalogEntry update(McpTemplateDefinition definition) {
        requireDefinition(definition);
        JdbcTemplate template = requireTemplate();
        int updated = template.update("""
                        UPDATE ai_ops_mcp_template
                        SET template_name = ?, resource_type = ?, transport_type = ?,
                            default_transport_config_json = ?, supported_actions_json = ?,
                            risk_level = ?, read_only = ?, description = ?, status = ?
                        WHERE template_id = ? AND status <> 'DELETED'
                        """,
                definition.templateName(),
                definition.resourceType(),
                definition.transportType(),
                JSON.toJSONString(definition.defaultTransportConfig()),
                JSON.toJSONString(definition.supportedActions()),
                definition.riskLevel(),
                definition.readOnly() ? 1 : 0,
                definition.description(),
                definition.status().name(),
                definition.templateId());
        requireUpdated(updated, definition.templateId());
        return findVisible(definition.templateId()).orElseThrow(() ->
                new IllegalStateException("MCP_TEMPLATE_UPDATE_NOT_VISIBLE:" + definition.templateId()));
    }

    @Override
    public McpTemplateCatalogEntry updateStatus(
            String templateId,
            McpTemplateStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_STATUS_REQUIRED");
        }
        int updated = requireTemplate().update("""
                        UPDATE ai_ops_mcp_template
                        SET status = ?
                        WHERE template_id = ? AND status <> 'DELETED'
                        """,
                status.name(),
                templateId);
        requireUpdated(updated, templateId);
        return findVisible(templateId).orElseThrow(() ->
                new IllegalStateException("MCP_TEMPLATE_STATUS_NOT_VISIBLE:" + templateId));
    }

    private McpTemplateCatalogEntry entry(ResultSet resultSet, int rowNum)
            throws SQLException {
        McpTemplateDefinition definition = new McpTemplateDefinition(
                resultSet.getString("template_id"),
                resultSet.getString("template_name"),
                resultSet.getString("resource_type"),
                resultSet.getString("transport_type"),
                map(resultSet.getString("default_transport_config_json")),
                strings(resultSet.getString("supported_actions_json")),
                resultSet.getString("risk_level"),
                resultSet.getBoolean("read_only"),
                resultSet.getString("description"),
                McpTemplateStatus.require(resultSet.getString("status")),
                resultSet.getString("create_by"));
        return new McpTemplateCatalogEntry(
                resultSet.getLong("id"),
                definition,
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private JdbcTemplate requireTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("MCP 模板数据库未配置");
        }
        ensureTable(template);
        return template;
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
                    CREATE TABLE IF NOT EXISTS ai_ops_mcp_template (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      template_id VARCHAR(120) NOT NULL COMMENT 'MCP模板ID',
                      template_name VARCHAR(160) NOT NULL COMMENT '模板名称',
                      resource_type VARCHAR(48) NOT NULL COMMENT '资源类型',
                      transport_type VARCHAR(32) NOT NULL DEFAULT 'stdio' COMMENT '传输类型',
                      default_transport_config_json LONGTEXT NOT NULL COMMENT '默认传输配置',
                      supported_actions_json TEXT NOT NULL COMMENT '支持动作',
                      risk_level VARCHAR(24) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
                      read_only TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否只读',
                      description TEXT NULL COMMENT '说明',
                      status VARCHAR(24) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                      create_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_template_id (template_id),
                      KEY idx_resource_status (resource_type, status),
                      KEY idx_risk_status (risk_level, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP模板表'
                    """);
            seedDefaults(template);
            initialized = true;
        }
    }

    private void seedDefaults(JdbcTemplate template) {
        for (McpTemplateDefinition definition : McpTemplateDefaults.values()) {
            template.update("""
                            INSERT INTO ai_ops_mcp_template
                            (template_id, template_name, resource_type, transport_type,
                             default_transport_config_json, supported_actions_json,
                             risk_level, read_only, description, status, create_by)
                            VALUES (?, ?, ?, 'stdio', ?, ?, ?, ?, ?, 'ENABLED', 'system')
                            ON DUPLICATE KEY UPDATE
                              template_name = VALUES(template_name),
                              resource_type = VALUES(resource_type),
                              supported_actions_json = VALUES(supported_actions_json),
                              risk_level = VALUES(risk_level),
                              read_only = VALUES(read_only),
                              description = VALUES(description)
                            """,
                    definition.templateId(),
                    definition.templateName(),
                    definition.resourceType(),
                    JSON.toJSONString(definition.defaultTransportConfig()),
                    JSON.toJSONString(definition.supportedActions()),
                    definition.riskLevel(),
                    definition.readOnly() ? 1 : 0,
                    definition.description());
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
            List<String> result = new ArrayList<>();
            for (Object item : array) {
                String value = item == null ? "" : String.valueOf(item).trim();
                if (!value.isBlank() && !result.contains(value)) {
                    result.add(value);
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException ignored) {
            return List.of(json.trim());
        }
    }

    private Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(object));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private void requireDefinition(McpTemplateDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_DEFINITION_REQUIRED");
        }
    }

    private void requireUpdated(int updated, String templateId) {
        if (updated == 0) {
            throw new IllegalArgumentException("MCP 模板不存在：" + templateId);
        }
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
