package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcIncidentSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.incidents.auto-init:true}")
    private boolean autoInit;

    public JdbcIncidentSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void initialize() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (!autoInit || jdbc == null) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_incident (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  incident_id VARCHAR(80) NOT NULL COMMENT '故障事件ID',
                  project_id VARCHAR(80) NOT NULL COMMENT '项目隔离边界',
                  title VARCHAR(240) NOT NULL COMMENT '事件标题',
                  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACKED/INVESTIGATING/MITIGATED/RESOLVED/REVIEWED',
                  severity VARCHAR(32) NOT NULL DEFAULT 'WARN' COMMENT '严重级别',
                  service_name VARCHAR(160) NULL COMMENT '服务名',
                  source_type VARCHAR(64) NULL COMMENT '来源类型',
                  fingerprint VARCHAR(160) NULL COMMENT '告警指纹',
                  dedup_key VARCHAR(160) NULL COMMENT '去重键',
                  current_run_id VARCHAR(80) NULL COMMENT '最近分析run',
                  owner_user_id VARCHAR(120) NULL COMMENT '当前事件责任人',
                  summary TEXT NULL COMMENT '事件摘要',
                  labels_json TEXT NULL COMMENT '标签JSON',
                  metadata_json TEXT NULL COMMENT '扩展JSON',
                  occurrence_count BIGINT NOT NULL DEFAULT 1 COMMENT '同一事件聚合次数',
                  affected_resources_json TEXT NULL COMMENT '受影响资源',
                  first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现',
                  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近出现',
                  acknowledged_at TIMESTAMP NULL COMMENT '确认时间',
                  resolved_at TIMESTAMP NULL COMMENT '恢复时间',
                  reviewed_at TIMESTAMP NULL COMMENT '复盘时间',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_incident_id (incident_id),
                  UNIQUE KEY uk_dedup_key (dedup_key),
                  KEY idx_project_status_update (project_id, status, update_time),
                  KEY idx_fingerprint (fingerprint)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维故障事件表'
                """);
        addColumnIfMissing(jdbc, "owner_user_id",
                "VARCHAR(120) NULL COMMENT '当前事件责任人' AFTER current_run_id");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_incident_timeline (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  incident_id VARCHAR(80) NOT NULL COMMENT '故障事件ID',
                  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
                  title VARCHAR(240) NOT NULL COMMENT '标题',
                  detail MEDIUMTEXT NULL COMMENT '详情',
                  actor VARCHAR(120) NULL COMMENT '操作者',
                  ref_type VARCHAR(64) NULL COMMENT '关联类型',
                  ref_id VARCHAR(160) NULL COMMENT '关联ID',
                  payload_json MEDIUMTEXT NULL COMMENT '扩展JSON',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_incident_time (incident_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维故障事件时间线'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_incident_run (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  incident_id VARCHAR(80) NOT NULL COMMENT '故障事件ID',
                  run_id VARCHAR(80) NOT NULL COMMENT '分析run',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_incident_run (incident_id, run_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障事件与分析run关联表'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_incident_watcher (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  incident_id VARCHAR(80) NOT NULL COMMENT '故障事件ID',
                  user_id VARCHAR(120) NOT NULL COMMENT '关注用户',
                  created_by VARCHAR(120) NULL COMMENT '添加人',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_incident_watcher (incident_id, user_id),
                  KEY idx_watcher_user (user_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障事件关注关系'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_incident_relation (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  incident_id_low VARCHAR(80) NOT NULL COMMENT '规范化较小Incident ID',
                  incident_id_high VARCHAR(80) NOT NULL COMMENT '规范化较大Incident ID',
                  relation_type VARCHAR(32) NOT NULL DEFAULT 'RELATED' COMMENT '关系类型',
                  created_by VARCHAR(120) NULL COMMENT '创建人',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_incident_relation (incident_id_low, incident_id_high, relation_type),
                  KEY idx_relation_high (incident_id_high, relation_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障事件关联关系'
                """);
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String columnName, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'ai_ops_incident'
                  AND column_name = ?
                """, Integer.class, columnName);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE ai_ops_incident ADD COLUMN " + columnName + " " + definition);
        }
    }
}
