package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAlertAggregationSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.alert-triggers.auto-init:true}")
    private boolean autoInit;

    public JdbcAlertAggregationSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void initialize() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (!autoInit || jdbc == null) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_alert_aggregate (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  aggregate_key VARCHAR(128) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  rule_id BIGINT NOT NULL,
                  fingerprint VARCHAR(160) NOT NULL,
                  current_state VARCHAR(24) NOT NULL DEFAULT 'FIRING',
                  severity VARCHAR(64) NOT NULL DEFAULT 'WARNING',
                  severity_rank INT NOT NULL DEFAULT 50,
                  occurrence_count BIGINT NOT NULL DEFAULT 1,
                  pending_summary_count INT NOT NULL DEFAULT 0,
                  affected_resources_json TEXT NULL,
                  payload_json MEDIUMTEXT NULL,
                  first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  next_summary_at TIMESTAMP NULL,
                  max_summary_at TIMESTAMP NULL,
                  summary_claim_token VARCHAR(64) NULL,
                  summary_claimed_at TIMESTAMP NULL,
                  last_dispatched_at TIMESTAMP NULL,
                  last_dispatch_type VARCHAR(32) NULL,
                  version BIGINT NOT NULL DEFAULT 1,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_alert_aggregate_key (aggregate_key),
                  KEY idx_alert_aggregate_due (current_state, next_summary_at, severity_rank),
                  KEY idx_alert_aggregate_project (project_id, last_seen_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警跨实例聚合状态'
                """);
        addColumnIfMissing(jdbc, "summary_claim_token",
                "VARCHAR(64) NULL COMMENT '摘要任务认领令牌'");
        addColumnIfMissing(jdbc, "summary_claimed_at",
                "TIMESTAMP NULL COMMENT '摘要任务认领时间'");
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String columnName, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'ai_ops_alert_aggregate'
                  AND column_name = ?
                """, Integer.class, columnName);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE ai_ops_alert_aggregate ADD COLUMN "
                    + columnName + " " + definition);
        }
    }
}
