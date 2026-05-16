CREATE TABLE IF NOT EXISTS `ai_ops_change_package` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `package_id` VARCHAR(80) NOT NULL,
  `session_id` VARCHAR(80) NOT NULL DEFAULT '',
  `incident_id` VARCHAR(80) NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `preparation_agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `preparation_agent_version` INT NOT NULL DEFAULT 0,
  `package_type` VARCHAR(48) NOT NULL,
  `status` VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
  `version` INT NOT NULL DEFAULT 1,
  `approved_version` INT DEFAULT NULL,
  `package_hash` VARCHAR(128) NOT NULL,
  `approved_package_hash` VARCHAR(128) DEFAULT NULL,
  `approved_snapshot_json` MEDIUMTEXT NULL,
  `objective` VARCHAR(1000) NOT NULL DEFAULT '',
  `summary` TEXT NULL,
  `evidence_json` MEDIUMTEXT NULL,
  `tool_bindings_json` MEDIUMTEXT NULL,
  `preflight_result_json` MEDIUMTEXT NULL,
  `dry_run_result_json` MEDIUMTEXT NULL,
  `validation_assessment` VARCHAR(48) NOT NULL DEFAULT '',
  `reason_code` VARCHAR(80) NOT NULL DEFAULT '',
  `approval_boundary_json` MEDIUMTEXT NULL,
  `preferred_plan_json` MEDIUMTEXT NULL,
  `adjustment_policy_json` MEDIUMTEXT NULL,
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'MEDIUM',
  `target_environment` VARCHAR(64) NOT NULL DEFAULT '',
  `target_scope_json` MEDIUMTEXT NULL,
  `allowed_tools_json` MEDIUMTEXT NULL,
  `forbidden_tools_json` MEDIUMTEXT NULL,
  `branch_name` VARCHAR(256) DEFAULT NULL,
  `base_branch` VARCHAR(256) DEFAULT NULL,
  `target_branch` VARCHAR(256) DEFAULT NULL,
  `base_commit` VARCHAR(128) DEFAULT NULL,
  `repair_workspace_id` VARCHAR(80) DEFAULT NULL,
  `repository_id` VARCHAR(128) DEFAULT NULL,
  `service_id` VARCHAR(128) DEFAULT NULL,
  `repair_commit` VARCHAR(128) DEFAULT NULL,
  `diff_summary` TEXT NULL,
  `diff_hash` VARCHAR(128) DEFAULT NULL,
  `test_command` VARCHAR(1024) DEFAULT NULL,
  `test_proof_hash` VARCHAR(128) DEFAULT NULL,
  `artifact_digest` VARCHAR(128) DEFAULT NULL,
  `changed_files_json` MEDIUMTEXT NULL,
  `code_evidence_json` MEDIUMTEXT NULL,
  `bash_evidence_json` MEDIUMTEXT NULL,
  `lsp_evidence_json` MEDIUMTEXT NULL,
  `mcp_steps_json` MEDIUMTEXT NULL,
  `rollback_steps_json` MEDIUMTEXT NULL,
  `verification_criteria_json` MEDIUMTEXT NULL,
  `ci_result_json` MEDIUMTEXT NULL,
  `landing_plan_json` MEDIUMTEXT NULL,
  `allowed_landing_adjustments_json` MEDIUMTEXT NULL,
  `landing_result_json` MEDIUMTEXT NULL,
  `landing_run_id` VARCHAR(80) NOT NULL DEFAULT '',
  `failure_summary_json` MEDIUMTEXT NULL,
  `cleanup_plan_json` MEDIUMTEXT NULL,
  `create_by` VARCHAR(128) NOT NULL DEFAULT '',
  `approve_by` VARCHAR(128) DEFAULT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `approved_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_id` (`package_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_project_status` (`project_id`, `status`, `update_time`),
  KEY `idx_incident_id` (`incident_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维变更包主表';

DROP PROCEDURE IF EXISTS add_ai_ops_change_package_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_change_package_column(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_change_package'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_change_package` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_ai_ops_change_package_column('tool_bindings_json', '`tool_bindings_json` MEDIUMTEXT NULL AFTER `evidence_json`');
CALL add_ai_ops_change_package_column('preflight_result_json', '`preflight_result_json` MEDIUMTEXT NULL AFTER `tool_bindings_json`');
CALL add_ai_ops_change_package_column('dry_run_result_json', '`dry_run_result_json` MEDIUMTEXT NULL AFTER `preflight_result_json`');
CALL add_ai_ops_change_package_column('validation_assessment', '`validation_assessment` VARCHAR(48) NOT NULL DEFAULT '''' AFTER `dry_run_result_json`');
CALL add_ai_ops_change_package_column('reason_code', '`reason_code` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `validation_assessment`');
CALL add_ai_ops_change_package_column('approval_boundary_json', '`approval_boundary_json` MEDIUMTEXT NULL AFTER `reason_code`');
CALL add_ai_ops_change_package_column('preferred_plan_json', '`preferred_plan_json` MEDIUMTEXT NULL AFTER `approval_boundary_json`');
CALL add_ai_ops_change_package_column('adjustment_policy_json', '`adjustment_policy_json` MEDIUMTEXT NULL AFTER `preferred_plan_json`');
CALL add_ai_ops_change_package_column('repair_workspace_id', '`repair_workspace_id` VARCHAR(80) DEFAULT NULL AFTER `base_commit`');
CALL add_ai_ops_change_package_column('repository_id', '`repository_id` VARCHAR(128) DEFAULT NULL AFTER `repair_workspace_id`');
CALL add_ai_ops_change_package_column('service_id', '`service_id` VARCHAR(128) DEFAULT NULL AFTER `repository_id`');
CALL add_ai_ops_change_package_column('diff_hash', '`diff_hash` VARCHAR(128) DEFAULT NULL AFTER `diff_summary`');
CALL add_ai_ops_change_package_column('test_command', '`test_command` VARCHAR(1024) DEFAULT NULL AFTER `diff_hash`');
CALL add_ai_ops_change_package_column('test_proof_hash', '`test_proof_hash` VARCHAR(128) DEFAULT NULL AFTER `test_command`');
CALL add_ai_ops_change_package_column('artifact_digest', '`artifact_digest` VARCHAR(128) DEFAULT NULL AFTER `test_proof_hash`');
CALL add_ai_ops_change_package_column('code_evidence_json', '`code_evidence_json` MEDIUMTEXT NULL AFTER `changed_files_json`');
CALL add_ai_ops_change_package_column('bash_evidence_json', '`bash_evidence_json` MEDIUMTEXT NULL AFTER `code_evidence_json`');
CALL add_ai_ops_change_package_column('lsp_evidence_json', '`lsp_evidence_json` MEDIUMTEXT NULL AFTER `bash_evidence_json`');
CALL add_ai_ops_change_package_column('landing_run_id', '`landing_run_id` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `landing_result_json`');
CALL add_ai_ops_change_package_column('cleanup_plan_json', '`cleanup_plan_json` MEDIUMTEXT NULL AFTER `failure_summary_json`');
CALL add_ai_ops_change_package_column('approved_snapshot_json', '`approved_snapshot_json` MEDIUMTEXT NULL AFTER `approved_package_hash`');

DROP PROCEDURE IF EXISTS add_ai_ops_change_package_column;

CREATE TABLE IF NOT EXISTS `ai_ops_change_package_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `package_id` VARCHAR(80) NOT NULL,
  `version` INT NOT NULL,
  `package_hash` VARCHAR(128) NOT NULL,
  `status` VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
  `snapshot_json` MEDIUMTEXT NOT NULL,
  `change_summary` TEXT NULL,
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_version` (`package_id`, `version`),
  KEY `idx_package_time` (`package_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维变更包版本表';

CREATE TABLE IF NOT EXISTS `ai_ops_change_package_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(80) NOT NULL,
  `package_id` VARCHAR(80) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `actor` VARCHAR(128) NOT NULL DEFAULT '',
  `summary` VARCHAR(1000) NOT NULL DEFAULT '',
  `payload_json` MEDIUMTEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_package_time` (`package_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维变更包事件表';

CREATE TABLE IF NOT EXISTS `ai_ops_change_package_landing_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(80) NOT NULL,
  `package_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `approved_version` INT NOT NULL,
  `approved_package_hash` VARCHAR(128) NOT NULL,
  `idempotency_key` VARCHAR(256) NOT NULL,
  `status` VARCHAR(48) NOT NULL DEFAULT 'RUNNING',
  `actor` VARCHAR(128) NOT NULL DEFAULT '',
  `lease_token` VARCHAR(128) NOT NULL DEFAULT '',
  `lease_expires_at` TIMESTAMP NULL DEFAULT NULL,
  `result_json` MEDIUMTEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `finished_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_package_time` (`package_id`, `create_time`),
  KEY `idx_status_lease` (`status`, `lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage LandingRun 表';

CREATE TABLE IF NOT EXISTS `ai_ops_change_package_landing_operation_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `operation_run_id` VARCHAR(80) NOT NULL,
  `landing_run_id` VARCHAR(80) NOT NULL,
  `package_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `approved_version` INT NOT NULL,
  `approved_package_hash` VARCHAR(128) NOT NULL,
  `operation_id` VARCHAR(128) NOT NULL DEFAULT '',
  `operation_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `adapter_type` VARCHAR(64) NOT NULL DEFAULT '',
  `toolset_id` VARCHAR(128) NOT NULL DEFAULT '',
  `tool_name` VARCHAR(256) NOT NULL DEFAULT '',
  `resource_key` VARCHAR(512) NOT NULL DEFAULT '',
  `effect_type` VARCHAR(64) NOT NULL DEFAULT '',
  `stage` VARCHAR(48) NOT NULL DEFAULT '',
  `status` VARCHAR(48) NOT NULL DEFAULT '',
  `result_id` VARCHAR(80) NOT NULL DEFAULT '',
  `output_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `reason_code` VARCHAR(128) NOT NULL DEFAULT '',
  `precondition_result_json` MEDIUMTEXT NULL,
  `execution_result_json` MEDIUMTEXT NULL,
  `post_check_result_json` MEDIUMTEXT NULL,
  `result_json` MEDIUMTEXT NULL,
  `started_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_run_id` (`operation_run_id`),
  KEY `idx_landing_run` (`landing_run_id`),
  KEY `idx_package_operation` (`package_id`, `operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage Landing operation run 表';

DROP PROCEDURE IF EXISTS add_ai_ops_landing_operation_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_landing_operation_column(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_change_package_landing_operation_run'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_change_package_landing_operation_run` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_ai_ops_landing_operation_column('operation_hash', '`operation_hash` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `operation_id`');
CALL add_ai_ops_landing_operation_column('adapter_type', '`adapter_type` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `operation_hash`');
CALL add_ai_ops_landing_operation_column('resource_key', '`resource_key` VARCHAR(512) NOT NULL DEFAULT '''' AFTER `tool_name`');
CALL add_ai_ops_landing_operation_column('effect_type', '`effect_type` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `resource_key`');
CALL add_ai_ops_landing_operation_column('stage', '`stage` VARCHAR(48) NOT NULL DEFAULT '''' AFTER `effect_type`');
CALL add_ai_ops_landing_operation_column('precondition_result_json', '`precondition_result_json` MEDIUMTEXT NULL AFTER `reason_code`');
CALL add_ai_ops_landing_operation_column('execution_result_json', '`execution_result_json` MEDIUMTEXT NULL AFTER `precondition_result_json`');
CALL add_ai_ops_landing_operation_column('post_check_result_json', '`post_check_result_json` MEDIUMTEXT NULL AFTER `execution_result_json`');
CALL add_ai_ops_landing_operation_column('started_at', '`started_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP AFTER `result_json`');
CALL add_ai_ops_landing_operation_column('finished_at', '`finished_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP AFTER `started_at`');

DROP PROCEDURE IF EXISTS add_ai_ops_landing_operation_column;

CREATE TABLE IF NOT EXISTS `ai_ops_change_resource_lock` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `resource_key` VARCHAR(512) NOT NULL,
  `package_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `run_id` VARCHAR(80) NOT NULL,
  `lease_token` VARCHAR(128) NOT NULL,
  `status` VARCHAR(48) NOT NULL DEFAULT 'ACTIVE',
  `lease_expires_at` TIMESTAMP NULL DEFAULT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_key` (`resource_key`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_status_lease` (`status`, `lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage Landing 资源锁表';

DROP PROCEDURE IF EXISTS add_ai_ops_resource_lock_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_resource_lock_column(IN column_name VARCHAR(64), IN column_def TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_change_resource_lock'
      AND COLUMN_NAME = column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_change_resource_lock` ADD COLUMN ', column_def);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DROP PROCEDURE IF EXISTS sync_ai_ops_resource_lock_key;
CREATE PROCEDURE sync_ai_ops_resource_lock_key()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_change_resource_lock'
      AND COLUMN_NAME = 'resource_lock_key'
  ) AND EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_change_resource_lock'
      AND COLUMN_NAME = 'resource_key'
  ) THEN
    UPDATE `ai_ops_change_resource_lock`
    SET `resource_key` = `resource_lock_key`
    WHERE (`resource_key` IS NULL OR `resource_key` = '')
      AND `resource_lock_key` IS NOT NULL
      AND `resource_lock_key` <> '';
  END IF;
END$$
DELIMITER ;

CALL add_ai_ops_resource_lock_column('resource_key', '`resource_key` VARCHAR(512) NOT NULL DEFAULT ''''');
CALL add_ai_ops_resource_lock_column('package_id', '`package_id` VARCHAR(80) NOT NULL DEFAULT ''''');
CALL add_ai_ops_resource_lock_column('project_id', '`project_id` VARCHAR(128) NOT NULL DEFAULT ''''');
CALL add_ai_ops_resource_lock_column('run_id', '`run_id` VARCHAR(80) NOT NULL DEFAULT ''''');
CALL add_ai_ops_resource_lock_column('status', '`status` VARCHAR(48) NOT NULL DEFAULT ''ACTIVE''');
CALL sync_ai_ops_resource_lock_key();

DROP PROCEDURE IF EXISTS add_ai_ops_resource_lock_column;
DROP PROCEDURE IF EXISTS sync_ai_ops_resource_lock_key;

CREATE TABLE IF NOT EXISTS `ai_ops_change_package_approval_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `approval_id` VARCHAR(80) NOT NULL,
  `package_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `version` INT NOT NULL,
  `package_hash` VARCHAR(128) NOT NULL,
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'MEDIUM',
  `approver` VARCHAR(128) NOT NULL DEFAULT '',
  `actor_scope` VARCHAR(32) NOT NULL DEFAULT '',
  `decision` VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
  `admin_confirmation` TINYINT NOT NULL DEFAULT 0,
  `metadata_json` MEDIUMTEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_approver_decision` (`package_id`, `version`, `package_hash`, `approver`, `decision`),
  KEY `idx_package_hash` (`package_id`, `version`, `package_hash`),
  KEY `idx_project_time` (`project_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage 风险审批记录表';
