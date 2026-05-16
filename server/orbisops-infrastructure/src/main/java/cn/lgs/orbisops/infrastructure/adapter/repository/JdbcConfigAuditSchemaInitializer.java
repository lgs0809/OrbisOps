package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JdbcConfigAuditSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final boolean autoInit;

    public JdbcConfigAuditSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${orbisops.config-audit.auto-init:true}") boolean autoInit) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (!autoInit) return;
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) return;
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_config_audit (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      audit_id VARCHAR(64) NULL COMMENT '审计业务ID',
                      project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '所属项目',
                      agent_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '关联Agent',
                      module_name VARCHAR(64) NOT NULL COMMENT '配置模块',
                      action_name VARCHAR(64) NOT NULL COMMENT '操作',
                      target_type VARCHAR(64) NOT NULL DEFAULT '' COMMENT '目标类型',
                      target_id VARCHAR(256) NOT NULL DEFAULT '' COMMENT '目标ID',
                      risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
                      result_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果',
                      operator_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '操作者ID',
                      operator_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '操作者名称',
                      operator_role VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作者角色',
                      client_ip VARCHAR(64) NOT NULL DEFAULT '' COMMENT '客户端IP',
                      trace_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '链路追踪ID',
                      before_json MEDIUMTEXT NULL COMMENT '变更前',
                      after_json MEDIUMTEXT NULL COMMENT '变更后',
                      create_time DATETIME NOT NULL COMMENT '创建时间',
                      PRIMARY KEY (id),
                      KEY idx_audit_id (audit_id),
                      KEY idx_project_time (project_id, create_time),
                      KEY idx_agent_time (agent_id, create_time),
                      KEY idx_operator_time (operator_id, create_time),
                      KEY idx_module_time (module_name, create_time),
                      KEY idx_target_time (target_id, create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维AI配置审计表'
                    """);
            ensureGovernanceColumns(jdbc);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_audit_policy (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      project_id VARCHAR(128) NOT NULL COMMENT '项目ID，GLOBAL 表示平台默认',
                      retention_days INT NOT NULL DEFAULT 180 COMMENT '审计保留天数',
                      masking_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用脱敏',
                      export_approval_required TINYINT NOT NULL DEFAULT 1 COMMENT '导出是否需要审批',
                      high_risk_confirmation_required TINYINT NOT NULL DEFAULT 1 COMMENT '高风险操作是否二次确认',
                      replay_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许审计回放',
                      status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
                      create_time DATETIME NOT NULL COMMENT '创建时间',
                      update_time DATETIME NOT NULL COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_project_id (project_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维审计策略表'
                    """);
            repairLegacyResultStatuses(jdbc);
        } catch (DataAccessException e) {
            throw new IllegalStateException("配置审计表初始化失败，禁止降级为内存审计：" + e.getMessage(), e);
        }
    }

    /**
     * Correct rows written before the audit command boundary carried an
     * explicit business outcome. The migration is intentionally conservative:
     * only SUCCESS rows whose action name is an unambiguous failure family are
     * changed, so ordinary actions such as reject remain successful commands.
     */
    private void repairLegacyResultStatuses(JdbcTemplate jdbc) {
        jdbc.update("""
                UPDATE ai_ops_config_audit
                   SET result_status = CASE
                       WHEN LOWER(action_name) LIKE '%blocked'
                         OR LOWER(action_name) LIKE '%denied'
                       THEN 'BLOCKED'
                       ELSE 'FAILED'
                   END
                 WHERE result_status = 'SUCCESS'
                   AND (LOWER(action_name) LIKE '%failed'
                     OR LOWER(action_name) LIKE '%failure'
                     OR LOWER(action_name) LIKE '%error'
                     OR LOWER(action_name) LIKE '%blocked'
                     OR LOWER(action_name) LIKE '%denied'
                     OR LOWER(action_name) LIKE '%timeout')
                """);
    }

    private void ensureGovernanceColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "audit_id", "VARCHAR(64) NULL COMMENT '审计业务ID' AFTER id");
        addColumnIfMissing(jdbc, "project_id", "VARCHAR(128) NOT NULL DEFAULT '' COMMENT '所属项目' AFTER audit_id");
        addColumnIfMissing(jdbc, "agent_id", "VARCHAR(128) NOT NULL DEFAULT '' COMMENT '关联Agent' AFTER project_id");
        addColumnIfMissing(jdbc, "target_type", "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '目标类型' AFTER action_name");
        alterColumnQuietly(jdbc, "target_id VARCHAR(256) NOT NULL DEFAULT '' COMMENT '目标ID'");
        addColumnIfMissing(jdbc, "risk_level", "VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '风险等级' AFTER target_id");
        addColumnIfMissing(jdbc, "result_status", "VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果' AFTER risk_level");
        alterColumnQuietly(jdbc, "result_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果'");
        addColumnIfMissing(jdbc, "trace_id", "VARCHAR(128) NOT NULL DEFAULT '' COMMENT '链路追踪ID' AFTER client_ip");
        addIndexIfMissing(jdbc, "idx_audit_id", "audit_id");
        addIndexIfMissing(jdbc, "idx_project_time", "project_id, create_time");
        addIndexIfMissing(jdbc, "idx_agent_time", "agent_id, create_time");
        addIndexIfMissing(jdbc, "idx_operator_time", "operator_id, create_time");
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String column, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_config_audit' AND COLUMN_NAME=?
                """, Integer.class, column);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE ai_ops_config_audit ADD COLUMN " + column + " " + definition);
        }
    }

    private void addIndexIfMissing(JdbcTemplate jdbc, String index, String columns) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_config_audit' AND INDEX_NAME=?
                """, Integer.class, index);
        if (count == null || count == 0) {
            jdbc.execute("CREATE INDEX " + index + " ON ai_ops_config_audit(" + columns + ")");
        }
    }

    private void alterColumnQuietly(JdbcTemplate jdbc, String definition) {
        try {
            jdbc.execute("ALTER TABLE ai_ops_config_audit MODIFY COLUMN " + definition);
        } catch (Exception e) {
            log.debug("配置审计字段兼容调整跳过：{}", e.getMessage());
        }
    }
}
