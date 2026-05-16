package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAlertOutboxSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.alert-triggers.auto-init:true}")
    private boolean autoInit;

    public JdbcAlertOutboxSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void initialize() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (!autoInit || jdbc == null) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_outbox (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  dedup_key VARCHAR(128) NOT NULL COMMENT '稳定调度键',
                  rule_id BIGINT NOT NULL COMMENT '规则ID',
                  project_id VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目隔离边界',
                  fingerprint VARCHAR(160) NOT NULL COMMENT '告警指纹',
                  aggregate_key VARCHAR(128) NULL COMMENT '告警聚合键',
                  event_type VARCHAR(32) NOT NULL DEFAULT 'FIRST' COMMENT '调度事件类型',
                  priority INT NOT NULL DEFAULT 50 COMMENT '调度优先级',
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/DEAD_LETTER',
                  run_id VARCHAR(80) NULL COMMENT '异步分析任务ID',
                  request_json MEDIUMTEXT NOT NULL COMMENT '待提交分析请求',
                  payload_json MEDIUMTEXT NULL COMMENT '原始告警',
                  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
                  next_retry_at TIMESTAMP NULL COMMENT '下次重试时间',
                  locked_token VARCHAR(64) NULL COMMENT '处理锁令牌',
                  locked_at TIMESTAMP NULL COMMENT '锁定时间',
                  dead_letter_at TIMESTAMP NULL COMMENT '进入死信时间',
                  error_message TEXT NULL COMMENT '错误信息',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_dedup_key (dedup_key),
                  KEY idx_status_retry (status, next_retry_at),
                  KEY idx_project_status_priority (project_id, status, priority),
                  KEY idx_rule_fingerprint (rule_id, fingerprint)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发 Outbox 表'
                """);
        addColumnIfMissing(jdbc, "locked_at", "TIMESTAMP NULL COMMENT '锁定时间'");
        addColumnIfMissing(jdbc, "dead_letter_at", "TIMESTAMP NULL COMMENT '进入死信时间'");
        addColumnIfMissing(jdbc, "project_id",
                "VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目隔离边界'");
        addColumnIfMissing(jdbc, "aggregate_key", "VARCHAR(128) NULL COMMENT '告警聚合键'");
        addColumnIfMissing(jdbc, "event_type",
                "VARCHAR(32) NOT NULL DEFAULT 'FIRST' COMMENT '调度事件类型'");
        addColumnIfMissing(jdbc, "priority", "INT NOT NULL DEFAULT 50 COMMENT '调度优先级'");
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String columnName, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'ai_ops_alert_trigger_outbox'
                  AND column_name = ?
                """, Integer.class, columnName);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE ai_ops_alert_trigger_outbox ADD COLUMN "
                    + columnName + " " + definition);
        }
    }
}
