package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Infrastructure-only compatibility initializer for ChangePackage persistence. */
@Component
public class JdbcChangePackageSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.change-package.auto-init:true}")
    private boolean autoInit;

    public JdbcChangePackageSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        if (!autoInit || jdbcTemplate == null) return;
        try {
            createTables();
            ensureCompatibilityColumns();
            backfillResourceLockKey();
        } catch (DataAccessException error) {
            throw new IllegalStateException(
                    "初始化 ChangePackage 表失败，安全主链路必须 fail closed：" + error.getMessage(), error);
        }
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_package (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  package_id VARCHAR(80) NOT NULL,
                  session_id VARCHAR(80) NOT NULL DEFAULT '',
                  incident_id VARCHAR(80) NULL,
                  project_id VARCHAR(128) NOT NULL,
                  preparation_agent_id VARCHAR(128) NOT NULL DEFAULT '',
                  preparation_agent_version INT NOT NULL DEFAULT 0,
                  package_type VARCHAR(48) NOT NULL,
                  status VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
                  version INT NOT NULL DEFAULT 1,
                  approved_version INT DEFAULT NULL,
                  package_hash VARCHAR(128) NOT NULL,
                  approved_package_hash VARCHAR(128) DEFAULT NULL,
                  approved_snapshot_json MEDIUMTEXT NULL,
                  objective VARCHAR(1000) NOT NULL DEFAULT '',
                  summary TEXT NULL,
                  evidence_json MEDIUMTEXT NULL,
                  tool_bindings_json MEDIUMTEXT NULL,
                  preflight_result_json MEDIUMTEXT NULL,
                  dry_run_result_json MEDIUMTEXT NULL,
                  validation_assessment VARCHAR(48) NOT NULL DEFAULT '',
                  reason_code VARCHAR(80) NOT NULL DEFAULT '',
                  approval_boundary_json MEDIUMTEXT NULL,
                  preferred_plan_json MEDIUMTEXT NULL,
                  adjustment_policy_json MEDIUMTEXT NULL,
                  risk_level VARCHAR(24) NOT NULL DEFAULT 'MEDIUM',
                  target_environment VARCHAR(64) NOT NULL DEFAULT '',
                  target_scope_json MEDIUMTEXT NULL,
                  allowed_tools_json MEDIUMTEXT NULL,
                  forbidden_tools_json MEDIUMTEXT NULL,
                  branch_name VARCHAR(256) DEFAULT NULL,
                  base_branch VARCHAR(256) DEFAULT NULL,
                  target_branch VARCHAR(256) DEFAULT NULL,
                  base_commit VARCHAR(128) DEFAULT NULL,
                  repair_workspace_id VARCHAR(80) DEFAULT NULL,
                  repository_id VARCHAR(128) DEFAULT NULL,
                  service_id VARCHAR(128) DEFAULT NULL,
                  repair_commit VARCHAR(128) DEFAULT NULL,
                  diff_summary TEXT NULL,
                  diff_hash VARCHAR(128) DEFAULT NULL,
                  test_command VARCHAR(1024) DEFAULT NULL,
                  test_proof_hash VARCHAR(128) DEFAULT NULL,
                  artifact_digest VARCHAR(128) DEFAULT NULL,
                  changed_files_json MEDIUMTEXT NULL,
                  code_evidence_json MEDIUMTEXT NULL,
                  bash_evidence_json MEDIUMTEXT NULL,
                  lsp_evidence_json MEDIUMTEXT NULL,
                  mcp_steps_json MEDIUMTEXT NULL,
                  rollback_steps_json MEDIUMTEXT NULL,
                  verification_criteria_json MEDIUMTEXT NULL,
                  ci_result_json MEDIUMTEXT NULL,
                  landing_plan_json MEDIUMTEXT NULL,
                  allowed_landing_adjustments_json MEDIUMTEXT NULL,
                  landing_result_json MEDIUMTEXT NULL,
                  landing_run_id VARCHAR(80) NOT NULL DEFAULT '',
                  failure_summary_json MEDIUMTEXT NULL,
                  cleanup_plan_json MEDIUMTEXT NULL,
                  create_by VARCHAR(128) NOT NULL DEFAULT '',
                  approve_by VARCHAR(128) DEFAULT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  approved_at TIMESTAMP NULL DEFAULT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_package_id (package_id),
                  KEY idx_session_id (session_id),
                  KEY idx_project_status (project_id, status, update_time),
                  KEY idx_incident_id (incident_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维变更包主表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_package_version (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  package_id VARCHAR(80) NOT NULL,
                  version INT NOT NULL,
                  package_hash VARCHAR(128) NOT NULL,
                  status VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
                  snapshot_json MEDIUMTEXT NOT NULL,
                  change_summary TEXT NULL,
                  created_by VARCHAR(128) NOT NULL DEFAULT '',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_package_version (package_id, version),
                  KEY idx_package_time (package_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维变更包版本表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_package_event (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  event_id VARCHAR(80) NOT NULL,
                  package_id VARCHAR(80) NOT NULL,
                  event_type VARCHAR(64) NOT NULL,
                  actor VARCHAR(128) NOT NULL DEFAULT '',
                  summary VARCHAR(1000) NOT NULL DEFAULT '',
                  payload_json MEDIUMTEXT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_event_id (event_id),
                  KEY idx_package_time (package_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维变更包事件表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_package_landing_run (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  run_id VARCHAR(80) NOT NULL,
                  package_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  approved_version INT NOT NULL,
                  approved_package_hash VARCHAR(128) NOT NULL,
                  idempotency_key VARCHAR(256) NOT NULL,
                  status VARCHAR(48) NOT NULL DEFAULT 'RUNNING',
                  actor VARCHAR(128) NOT NULL DEFAULT '',
                  lease_token VARCHAR(128) NOT NULL DEFAULT '',
                  lease_expires_at TIMESTAMP NULL DEFAULT NULL,
                  result_json MEDIUMTEXT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  finished_at TIMESTAMP NULL DEFAULT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_run_id (run_id),
                  UNIQUE KEY uk_idempotency_key (idempotency_key),
                  KEY idx_package_time (package_id, create_time),
                  KEY idx_status_lease (status, lease_expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage LandingRun 表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_package_landing_operation_run (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  operation_run_id VARCHAR(80) NOT NULL,
                  landing_run_id VARCHAR(80) NOT NULL,
                  package_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  approved_version INT NOT NULL,
                  approved_package_hash VARCHAR(128) NOT NULL,
                  operation_id VARCHAR(128) NOT NULL DEFAULT '',
                  operation_hash VARCHAR(128) NOT NULL DEFAULT '',
                  execution_key VARCHAR(160) NOT NULL DEFAULT '',
                  adapter_type VARCHAR(64) NOT NULL DEFAULT '',
                  toolset_id VARCHAR(128) NOT NULL DEFAULT '',
                  tool_name VARCHAR(256) NOT NULL DEFAULT '',
                  resource_key VARCHAR(512) NOT NULL DEFAULT '',
                  effect_type VARCHAR(64) NOT NULL DEFAULT '',
                  stage VARCHAR(48) NOT NULL DEFAULT '',
                  status VARCHAR(48) NOT NULL DEFAULT '',
                  fact_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
                  dispatch_attempts INT NOT NULL DEFAULT 0,
                  state_version BIGINT NOT NULL DEFAULT 0,
                  fencing_token BIGINT NOT NULL DEFAULT 0,
                  worker_id VARCHAR(128) NOT NULL DEFAULT '',
                  lease_expires_at DATETIME(3) NULL,
                  claimed_at TIMESTAMP NULL DEFAULT NULL,
                  remote_request_id VARCHAR(160) NOT NULL DEFAULT '',
                  remote_result_id VARCHAR(160) NOT NULL DEFAULT '',
                  request_hash VARCHAR(128) NOT NULL DEFAULT '',
                  operation_snapshot_json LONGTEXT NULL,
                  dispatched_at DATETIME(3) NULL,
                  acknowledged_at DATETIME(3) NULL,
                  verified_at DATETIME(3) NULL,
                  unknown_reason_code VARCHAR(128) NOT NULL DEFAULT '',
                  result_id VARCHAR(80) NOT NULL DEFAULT '',
                  output_hash VARCHAR(128) NOT NULL DEFAULT '',
                  reason_code VARCHAR(128) NOT NULL DEFAULT '',
                  precondition_result_json MEDIUMTEXT NULL,
                  execution_result_json MEDIUMTEXT NULL,
                  post_check_result_json MEDIUMTEXT NULL,
                  rollback_status VARCHAR(32) NOT NULL DEFAULT '',
                  rollback_result_json MEDIUMTEXT NULL,
                  rollback_at TIMESTAMP NULL DEFAULT NULL,
                  result_json MEDIUMTEXT NULL,
                  started_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
                  finished_at TIMESTAMP NULL DEFAULT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_operation_run_id (operation_run_id),
                  UNIQUE KEY uk_operation_execution_key (execution_key),
                  KEY idx_landing_run (landing_run_id),
                  KEY idx_package_operation (package_id, operation_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage Landing operation run 表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_resource_lock (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  resource_key VARCHAR(512) NOT NULL,
                  package_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  run_id VARCHAR(80) NOT NULL,
                  lease_token VARCHAR(128) NOT NULL,
                  status VARCHAR(48) NOT NULL DEFAULT 'ACTIVE',
                  lease_expires_at TIMESTAMP NULL DEFAULT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_resource_key (resource_key),
                  KEY idx_run_id (run_id),
                  KEY idx_status_lease (status, lease_expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage Landing 资源锁表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_change_package_approval_record (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  approval_id VARCHAR(80) NOT NULL,
                  package_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  version INT NOT NULL,
                  package_hash VARCHAR(128) NOT NULL,
                  risk_level VARCHAR(24) NOT NULL DEFAULT 'MEDIUM',
                  approver VARCHAR(128) NOT NULL DEFAULT '',
                  actor_scope VARCHAR(32) NOT NULL DEFAULT '',
                  decision VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
                  admin_confirmation TINYINT NOT NULL DEFAULT 0,
                  metadata_json MEDIUMTEXT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_package_approver_decision (package_id, version, package_hash, approver, decision),
                  KEY idx_package_hash (package_id, version, package_hash),
                  KEY idx_project_time (project_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage 风险审批记录表'
                """);
    }

    private void ensureCompatibilityColumns() {
        ensureColumn("ai_ops_change_package", "tool_bindings_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "preflight_result_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "dry_run_result_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "validation_assessment", "VARCHAR(48) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package", "reason_code", "VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package", "approval_boundary_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "preferred_plan_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "adjustment_policy_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "cleanup_plan_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "repair_workspace_id", "VARCHAR(80) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "repository_id", "VARCHAR(128) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "service_id", "VARCHAR(128) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "diff_hash", "VARCHAR(128) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "test_command", "VARCHAR(1024) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "test_proof_hash", "VARCHAR(128) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "artifact_digest", "VARCHAR(128) DEFAULT NULL");
        ensureColumn("ai_ops_change_package", "code_evidence_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "bash_evidence_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "lsp_evidence_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package", "landing_run_id", "VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package", "approved_snapshot_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "operation_hash", "VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "execution_key", "VARCHAR(160) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "adapter_type", "VARCHAR(64) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "resource_key", "VARCHAR(512) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "effect_type", "VARCHAR(64) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "stage", "VARCHAR(48) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "fact_status", "VARCHAR(32) NOT NULL DEFAULT 'NONE'");
        ensureColumn("ai_ops_change_package_landing_operation_run", "dispatch_attempts", "INT NOT NULL DEFAULT 0");
        ensureColumn("ai_ops_change_package_landing_operation_run", "state_version", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("ai_ops_change_package_landing_operation_run", "fencing_token", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("ai_ops_change_package_landing_operation_run", "worker_id", "VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "lease_expires_at", "DATETIME(3) NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "remote_request_id", "VARCHAR(160) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "remote_result_id", "VARCHAR(160) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "request_hash", "VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "operation_snapshot_json", "LONGTEXT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "dispatched_at", "DATETIME(3) NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "acknowledged_at", "DATETIME(3) NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "verified_at", "DATETIME(3) NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "unknown_reason_code", "VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "claimed_at", "TIMESTAMP NULL DEFAULT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "precondition_result_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "execution_result_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "post_check_result_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "rollback_status", "VARCHAR(32) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_package_landing_operation_run", "rollback_result_json", "MEDIUMTEXT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "rollback_at", "TIMESTAMP NULL DEFAULT NULL");
        ensureColumn("ai_ops_change_package_landing_operation_run", "started_at", "TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_ops_change_package_landing_operation_run", "finished_at", "TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_ops_change_resource_lock", "resource_key", "VARCHAR(512) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_resource_lock", "package_id", "VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_resource_lock", "project_id", "VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_resource_lock", "run_id", "VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_change_resource_lock", "status", "VARCHAR(48) NOT NULL DEFAULT 'ACTIVE'");
    }

    private void backfillResourceLockKey() {
        if (!columnExists("ai_ops_change_resource_lock", "resource_lock_key")
                || !columnExists("ai_ops_change_resource_lock", "resource_key")) return;
        jdbcTemplate.update("""
                UPDATE ai_ops_change_resource_lock
                SET resource_key = resource_lock_key
                WHERE (resource_key IS NULL OR resource_key = '')
                  AND resource_lock_key IS NOT NULL
                  AND resource_lock_key <> ''
                """);
    }

    private void ensureColumn(String table, String column, String definition) {
        try {
            if (!columnExists(table, column)) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        } catch (RuntimeException error) {
            throw new IllegalStateException("ChangePackage 字段兼容检查失败，table="
                    + table + " column=" + column + "：" + error.getMessage(), error);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
