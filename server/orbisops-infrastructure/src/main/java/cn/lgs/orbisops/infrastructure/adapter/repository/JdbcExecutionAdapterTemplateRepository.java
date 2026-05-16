package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionAdapterTemplateRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import cn.lgs.orbisops.domain.execution.service.ExecutionAdapterTemplateDefaults;
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
public class JdbcExecutionAdapterTemplateRepository
        implements IExecutionAdapterTemplateRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.execution-adapter-template.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcExecutionAdapterTemplateRepository(
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
    public List<ExecutionAdapterTemplate> list() {
        JdbcTemplate template = requireTemplate();
        return List.copyOf(template.query("""
                        SELECT id, adapter_template_id, template_name, adapter_type,
                               supported_actions_json, default_config_json, risk_level,
                               read_only, description, status, create_by, create_time,
                               update_time
                        FROM ai_ops_execution_adapter_template
                        WHERE status <> 'DELETED'
                        ORDER BY FIELD(risk_level, 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'), id ASC
                        """,
                this::template));
    }

    @Override
    public Optional<ExecutionAdapterTemplate> find(String templateId) {
        JdbcTemplate template = requireTemplate();
        List<ExecutionAdapterTemplate> values = template.query("""
                        SELECT id, adapter_template_id, template_name, adapter_type,
                               supported_actions_json, default_config_json, risk_level,
                               read_only, description, status, create_by, create_time,
                               update_time
                        FROM ai_ops_execution_adapter_template
                        WHERE adapter_template_id = ? AND status <> 'DELETED'
                        LIMIT 1
                        """,
                this::template,
                templateId);
        return values.stream().findFirst();
    }

    @Override
    public boolean exists(String templateId) {
        JdbcTemplate template = requireTemplate();
        Long count = template.queryForObject("""
                        SELECT COUNT(1)
                        FROM ai_ops_execution_adapter_template
                        WHERE adapter_template_id = ? AND status <> 'DELETED'
                        """,
                Long.class,
                templateId);
        return count != null && count > 0;
    }

    @Override
    public ExecutionAdapterTemplate insert(ExecutionAdapterTemplate value) {
        if (value == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_REQUIRED");
        }
        JdbcTemplate template = requireTemplate();
        template.update("""
                        INSERT INTO ai_ops_execution_adapter_template
                        (adapter_template_id, template_name, adapter_type,
                         supported_actions_json, default_config_json, risk_level,
                         read_only, description, status, create_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                value.templateId(),
                value.templateName(),
                value.adapterType().code(),
                JSON.toJSONString(value.supportedActions()),
                JSON.toJSONString(value.defaultConfig()),
                value.riskLevel().name(),
                value.readOnly() ? 1 : 0,
                value.description(),
                value.status().name(),
                value.createBy());
        return find(value.templateId()).orElse(value);
    }

    @Override
    public ExecutionAdapterTemplate update(ExecutionAdapterTemplate value) {
        if (value == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_REQUIRED");
        }
        JdbcTemplate template = requireTemplate();
        int updated = template.update("""
                        UPDATE ai_ops_execution_adapter_template
                        SET template_name = ?, adapter_type = ?, supported_actions_json = ?,
                            default_config_json = ?, risk_level = ?, read_only = ?,
                            description = ?, status = ?
                        WHERE adapter_template_id = ? AND status <> 'DELETED'
                        """,
                value.templateName(),
                value.adapterType().code(),
                JSON.toJSONString(value.supportedActions()),
                JSON.toJSONString(value.defaultConfig()),
                value.riskLevel().name(),
                value.readOnly() ? 1 : 0,
                value.description(),
                value.status().name(),
                value.templateId());
        requireUpdated(updated, value.templateId());
        return find(value.templateId()).orElse(value);
    }

    @Override
    public ExecutionAdapterTemplate updateStatus(
            String templateId,
            ExecutionResourceStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_STATUS_REQUIRED");
        }
        JdbcTemplate template = requireTemplate();
        int updated = template.update("""
                        UPDATE ai_ops_execution_adapter_template
                        SET status = ?
                        WHERE adapter_template_id = ? AND status <> 'DELETED'
                        """,
                status.name(),
                templateId);
        requireUpdated(updated, templateId);
        return find(templateId).orElseThrow(() -> new IllegalArgumentException(
                "执行适配器模板不存在：" + templateId));
    }

    private ExecutionAdapterTemplate template(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new ExecutionAdapterTemplate(
                resultSet.getLong("id"),
                resultSet.getString("adapter_template_id"),
                resultSet.getString("template_name"),
                ExecutionAdapterType.require(resultSet.getString("adapter_type")),
                strings(resultSet.getString("supported_actions_json")),
                map(resultSet.getString("default_config_json")),
                ExecutionRiskLevel.require(resultSet.getString("risk_level")),
                resultSet.getBoolean("read_only"),
                resultSet.getString("description"),
                ExecutionResourceStatus.require(resultSet.getString("status")),
                resultSet.getString("create_by"),
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private JdbcTemplate requireTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("执行适配器模板数据库未配置");
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
                    CREATE TABLE IF NOT EXISTS ai_ops_execution_adapter_template (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      adapter_template_id VARCHAR(120) NOT NULL COMMENT '执行适配器模板ID',
                      template_name VARCHAR(160) NOT NULL COMMENT '模板名称',
                      adapter_type VARCHAR(48) NOT NULL COMMENT '适配器类型',
                      supported_actions_json TEXT NOT NULL COMMENT '支持动作',
                      default_config_json LONGTEXT NOT NULL COMMENT '默认配置',
                      risk_level VARCHAR(24) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
                      read_only TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否只读',
                      description TEXT NULL COMMENT '说明',
                      status VARCHAR(24) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                      create_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_adapter_template_id (adapter_template_id),
                      KEY idx_adapter_status (adapter_type, status),
                      KEY idx_risk_status (risk_level, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行适配器模板表'
                    """);
            seedDefaults(template);
            initialized = true;
        }
    }

    private void seedDefaults(JdbcTemplate template) {
        for (ExecutionAdapterTemplate value : ExecutionAdapterTemplateDefaults.values()) {
            template.update("""
                            INSERT INTO ai_ops_execution_adapter_template
                            (adapter_template_id, template_name, adapter_type,
                             supported_actions_json, default_config_json, risk_level,
                             read_only, description, status, create_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', 'system')
                            ON DUPLICATE KEY UPDATE
                              template_name = VALUES(template_name),
                              adapter_type = VALUES(adapter_type),
                              supported_actions_json = VALUES(supported_actions_json),
                              risk_level = VALUES(risk_level),
                              read_only = VALUES(read_only),
                              description = VALUES(description)
                            """,
                    value.templateId(),
                    value.templateName(),
                    value.adapterType().code(),
                    JSON.toJSONString(value.supportedActions()),
                    JSON.toJSONString(value.defaultConfig()),
                    value.riskLevel().name(),
                    value.readOnly() ? 1 : 0,
                    value.description());
        }
    }

    private void requireUpdated(int updated, String templateId) {
        if (updated == 0) {
            throw new IllegalArgumentException(
                    "执行适配器模板不存在：" + templateId);
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

    private LocalDateTime time(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
