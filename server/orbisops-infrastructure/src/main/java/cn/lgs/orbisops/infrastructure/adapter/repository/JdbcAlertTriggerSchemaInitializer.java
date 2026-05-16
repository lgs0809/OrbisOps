package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAlertTriggerSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.alert-triggers.auto-init:true}")
    private boolean autoInit;

    public JdbcAlertTriggerSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void initialize() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (!autoInit || jdbc == null) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_rule (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
                  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
                  source_type VARCHAR(32) NOT NULL DEFAULT 'ALERTMANAGER' COMMENT '来源类型',
                  alert_name_regex VARCHAR(255) NULL COMMENT 'alertname 正则',
                  severity_regex VARCHAR(255) NULL COMMENT 'severity 正则',
                  service_regex VARCHAR(255) NULL COMMENT 'service/app/job 正则',
                  match_labels_json TEXT NULL COMMENT '标签匹配 JSON，值以 ~ 开头表示正则',
                  notification_channel_id VARCHAR(80) NULL COMMENT '通知 Channel ID',
                  notification_target VARCHAR(256) NULL COMMENT '通知目标',
                  webhook_secret VARCHAR(160) NULL COMMENT 'Webhook签名密钥',
                  project_id VARCHAR(80) NOT NULL COMMENT '业务系统ID',
                  agent_definition_id VARCHAR(128) NULL COMMENT 'Agent 定义 ID',
                  agent_binding_mode VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED' COMMENT 'Agent版本绑定模式',
                  agent_version INT NULL COMMENT '绑定或最近解析的Agent版本',
                  agent_definition_hash VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent定义哈希',
                  question_template TEXT NULL COMMENT '问题模板',
                  range_minutes INT NOT NULL DEFAULT 15 COMMENT '分析窗口',
                  prom_window VARCHAR(16) NOT NULL DEFAULT '5m' COMMENT 'Prometheus 窗口',
                  include_recent_logs TINYINT NOT NULL DEFAULT 1 COMMENT '是否查最近日志',
                  notify_channel TINYINT NOT NULL DEFAULT 0 COMMENT '是否通过 Channel 推送',
                  sub_agent_max_iterations INT NULL COMMENT '子 Agent 循环上限',
                  node_timeout_seconds INT NULL COMMENT '节点超时秒',
                  max_evidence_items INT NULL COMMENT '证据条数上限',
                  dedup_window_seconds INT NOT NULL DEFAULT 900 COMMENT '去重窗口秒',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_status_source (status, source_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发规则表'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_event (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  rule_id BIGINT NULL COMMENT '规则ID',
                  rule_name VARCHAR(128) NULL COMMENT '规则名称',
                  project_id VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目隔离边界',
                  source_type VARCHAR(32) NOT NULL COMMENT '来源类型',
                  status VARCHAR(32) NOT NULL COMMENT '处理状态',
                  dedup_key VARCHAR(128) NULL COMMENT '幂等调度键',
                  fingerprint VARCHAR(160) NOT NULL COMMENT '告警指纹',
                  alert_name VARCHAR(160) NULL COMMENT '告警名',
                  severity VARCHAR(64) NULL COMMENT '级别',
                  service_name VARCHAR(160) NULL COMMENT '服务名',
                  receiver VARCHAR(160) NULL COMMENT '接收人',
                  run_id VARCHAR(80) NULL COMMENT '异步分析任务ID',
                  run_status VARCHAR(32) NULL COMMENT '异步分析最终状态',
                  final_summary TEXT NULL COMMENT '最终摘要',
                  completed_at TIMESTAMP NULL COMMENT '完成时间',
                  error_message TEXT NULL COMMENT '错误信息',
                  labels_json TEXT NULL COMMENT '告警标签',
                  annotations_json TEXT NULL COMMENT '告警注解',
                  payload_json MEDIUMTEXT NULL COMMENT '原始告警',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_rule_fingerprint_time (rule_id, fingerprint, create_time),
                  KEY idx_status_time (status, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发事件表'
                """);
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "webhook_secret",
                "VARCHAR(160) NULL COMMENT 'Webhook签名密钥'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "project_id",
                "VARCHAR(80) NULL COMMENT '业务系统ID'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "notification_channel_id",
                "VARCHAR(80) NULL COMMENT '通知 Channel ID'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "notification_target",
                "VARCHAR(256) NULL COMMENT '通知目标'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "notify_channel",
                "TINYINT NOT NULL DEFAULT 0 COMMENT '是否通过 Channel 推送'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "agent_binding_mode",
                "VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED' COMMENT 'Agent版本绑定模式'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "agent_version",
                "INT NULL COMMENT '绑定或最近解析的Agent版本'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_rule", "agent_definition_hash",
                "VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent定义哈希'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_event", "dedup_key",
                "VARCHAR(128) NULL COMMENT '幂等调度键'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_event", "project_id",
                "VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目隔离边界'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_event", "run_status",
                "VARCHAR(32) NULL COMMENT '异步分析最终状态'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_event", "final_summary",
                "TEXT NULL COMMENT '最终摘要'");
        addColumnIfMissing(jdbc, "ai_ops_alert_trigger_event", "completed_at",
                "TIMESTAMP NULL COMMENT '完成时间'");
    }

    private void addColumnIfMissing(
            JdbcTemplate jdbc,
            String tableName,
            String columnName,
            String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name=? AND column_name=?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}
