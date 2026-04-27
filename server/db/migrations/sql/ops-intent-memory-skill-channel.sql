-- Intent / explicit memory / skill release / evidence / channel / progressive disclosure hardening.

DROP PROCEDURE IF EXISTS add_ops_platform_column;
DELIMITER //
CREATE PROCEDURE add_ops_platform_column(IN p_table_name VARCHAR(128), IN p_column_name VARCHAR(128), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_ops_platform_column('ai_ops_tool_result', 'input_hash', '`input_hash` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `query_text`');
CALL add_ops_platform_column('ai_ops_tool_result', 'status', '`status` VARCHAR(24) NOT NULL DEFAULT ''SUCCEEDED'' AFTER `source`');
CALL add_ops_platform_column('ai_ops_tool_result', 'duration_ms', '`duration_ms` BIGINT NOT NULL DEFAULT 0 AFTER `truncated`');

DROP PROCEDURE IF EXISTS add_ops_platform_column;

CREATE TABLE IF NOT EXISTS `ai_ops_evidence` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `evidence_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `run_id` VARCHAR(100) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_id` VARCHAR(128) NOT NULL DEFAULT '',
  `tool_result_id` VARCHAR(100) NOT NULL,
  `output_hash` VARCHAR(64) NOT NULL,
  `full_output_ref` VARCHAR(256) NOT NULL,
  `summary` TEXT NULL,
  `verified` TINYINT NOT NULL DEFAULT 0,
  `metadata_json` MEDIUMTEXT NULL,
  `idempotency_key` VARCHAR(64) NOT NULL,
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evidence_id` (`evidence_id`),
  UNIQUE KEY `uk_evidence_idempotency` (`idempotency_key`),
  KEY `idx_evidence_run` (`project_id`, `run_id`, `created_at`),
  KEY `idx_evidence_result` (`tool_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一运维证据引用';

CREATE TABLE IF NOT EXISTS `ai_ops_memory` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `memory_id` VARCHAR(80) NOT NULL,
  `scope_type` VARCHAR(24) NOT NULL,
  `scope_id` VARCHAR(128) NOT NULL,
  `user_id` VARCHAR(128) NOT NULL DEFAULT '',
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `session_id` VARCHAR(100) NOT NULL DEFAULT '',
  `memory_type` VARCHAR(64) NOT NULL,
  `logical_key` VARCHAR(256) NOT NULL,
  `content` MEDIUMTEXT NOT NULL,
  `normalized_content` MEDIUMTEXT NOT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'USER_ASSERTED',
  `source_run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `verified` TINYINT NOT NULL DEFAULT 0,
  `confidence` DECIMAL(5,4) NOT NULL DEFAULT 0.6000,
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'LOW',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `version` INT NOT NULL DEFAULT 1,
  `memory_hash` VARCHAR(64) NOT NULL,
  `proof_refs_json` MEDIUMTEXT NULL,
  `expires_at` TIMESTAMP NULL DEFAULT NULL,
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `idempotency_key` VARCHAR(64) NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_id` (`memory_id`),
  UNIQUE KEY `uk_memory_idempotency` (`idempotency_key`),
  KEY `idx_memory_scope` (`scope_type`, `scope_id`, `status`, `update_time`),
  KEY `idx_memory_project` (`project_id`, `update_time`),
  KEY `idx_memory_run` (`source_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='显式Memory及版本指针';

CREATE TABLE IF NOT EXISTS `ai_ops_memory_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `memory_id` VARCHAR(80) NOT NULL,
  `version` INT NOT NULL,
  `memory_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `content` MEDIUMTEXT NOT NULL,
  `normalized_content` MEDIUMTEXT NOT NULL,
  `source_run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_version` (`memory_id`, `version`),
  KEY `idx_memory_version_hash` (`memory_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Memory append-only版本';

CREATE TABLE IF NOT EXISTS `ai_ops_memory_conflict` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conflict_id` VARCHAR(80) NOT NULL,
  `scope_type` VARCHAR(24) NOT NULL,
  `scope_id` VARCHAR(128) NOT NULL,
  `logical_key` VARCHAR(256) NOT NULL,
  `existing_memory_id` VARCHAR(80) NOT NULL,
  `incoming_memory_id` VARCHAR(80) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  `resolution_json` MEDIUMTEXT NULL,
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_conflict_id` (`conflict_id`),
  KEY `idx_memory_conflict_scope` (`scope_type`, `scope_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='显式Memory冲突记录';

CREATE TABLE IF NOT EXISTS `ai_ops_memory_audit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `audit_id` VARCHAR(80) NOT NULL,
  `memory_id` VARCHAR(80) NOT NULL,
  `action` VARCHAR(64) NOT NULL,
  `actor` VARCHAR(128) NOT NULL DEFAULT '',
  `payload_json` MEDIUMTEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_audit_id` (`audit_id`),
  KEY `idx_memory_audit_memory` (`memory_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Memory治理审计';

CREATE TABLE IF NOT EXISTS `ai_ops_mcp_tool_activation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `activation_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `run_id` VARCHAR(100) NOT NULL,
  `session_id` VARCHAR(100) NOT NULL DEFAULT '',
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `mcp_id` VARCHAR(128) NOT NULL,
  `tool_name` VARCHAR(256) NOT NULL,
  `schema_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `disclosure_tier` VARCHAR(24) NOT NULL DEFAULT 'EXTENSION',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` TIMESTAMP NOT NULL,
  `metadata_json` MEDIUMTEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_activation_id` (`activation_id`),
  UNIQUE KEY `uk_mcp_activation_run_tool` (`project_id`, `run_id`, `mcp_id`, `tool_name`),
  KEY `idx_mcp_activation_expiry` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Work Session按需激活MCP工具';

CREATE TABLE IF NOT EXISTS `ai_ops_channel` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `execution_kind` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/REACT/WORKFLOW；Channel 是 Transport，执行选择与渠道身份正交',
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '兼容列：仅 WORKFLOW 时保存 workflowId；REACT/NONE 为空',
  `agent_binding_mode` VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED' COMMENT '兼容列：Workflow 版本策略',
  `agent_version` INT NULL COMMENT '兼容列：Pinned Workflow exact version',
  `agent_definition_hash` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '兼容列：已解析 Workflow definition hash',
  `name` VARCHAR(128) NOT NULL,
  `channel_type` VARCHAR(32) NOT NULL,
  `credential_ref` VARCHAR(256) NOT NULL DEFAULT '',
  `config_json` MEDIUMTEXT NULL,
  `access_policy` VARCHAR(32) NOT NULL DEFAULT 'DENY_UNKNOWN' COMMENT 'DENY_UNKNOWN/PAIRING/ALLOWLIST/OBSERVE_ONLY_UNKNOWN',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_channel_id` (`channel_id`),
  KEY `idx_ops_channel_project` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一消息Transport配置；入站执行可选NONE/REACT/WORKFLOW';

CREATE TABLE IF NOT EXISTS `ai_ops_channel_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel_session_id` VARCHAR(80) NOT NULL,
  `channel_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL,
  `external_conversation_id` VARCHAR(256) NOT NULL,
  `sender_id` VARCHAR(256) NOT NULL,
  `session_id` VARCHAR(100) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_session_id` (`channel_session_id`),
  UNIQUE KEY `uk_channel_external_session` (`channel_id`, `external_conversation_id`, `sender_id`),
  KEY `idx_channel_internal_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道会话映射';

CREATE TABLE IF NOT EXISTS `ai_ops_channel_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id` VARCHAR(80) NOT NULL,
  `channel_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `external_message_id` VARCHAR(256) NOT NULL,
  `external_conversation_id` VARCHAR(256) NOT NULL,
  `sender_id` VARCHAR(256) NOT NULL,
  `session_id` VARCHAR(100) NOT NULL DEFAULT '',
  `run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `direction` VARCHAR(16) NOT NULL,
  `status` VARCHAR(24) NOT NULL,
  `payload_json` MEDIUMTEXT NULL,
  `error_message` TEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_message_id` (`message_id`),
  UNIQUE KEY `uk_channel_external_message` (`channel_id`, `external_message_id`),
  KEY `idx_channel_message_run` (`project_id`, `run_id`),
  KEY `idx_channel_message_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道消息去重和投递状态';

CREATE TABLE IF NOT EXISTS `ai_ops_channel_approval_action` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `action_id` VARCHAR(96) NOT NULL,
  `token_hash` CHAR(64) NOT NULL,
  `channel_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `package_id` VARCHAR(96) NOT NULL,
  `package_version` INT NOT NULL,
  `package_hash` VARCHAR(128) NOT NULL,
  `decision` VARCHAR(16) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `issued_by` VARCHAR(128) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `consumed_by` VARCHAR(128) NOT NULL DEFAULT '',
  `consumed_at` DATETIME(3) NULL,
  `terminal_reason` VARCHAR(256) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_approval_action_id` (`action_id`),
  UNIQUE KEY `uk_channel_approval_token_hash` (`token_hash`),
  KEY `idx_channel_approval_package` (`project_id`, `package_id`, `package_version`, `status`),
  KEY `idx_channel_approval_expiry` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel opaque approval action token ledger';

CREATE TABLE IF NOT EXISTS `ai_ops_channel_approval_action_actor` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `token_hash` CHAR(64) NOT NULL,
  `actor` VARCHAR(128) NOT NULL,
  `claimed_at` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_approval_action_actor` (`token_hash`, `actor`),
  KEY `idx_channel_approval_actor` (`actor`, `claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-actor idempotency for Channel approval actions';

CREATE TABLE IF NOT EXISTS `ai_ops_workflow_approval` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `approval_id` VARCHAR(96) NOT NULL,
  `run_id` VARCHAR(100) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `node_id` VARCHAR(128) NOT NULL,
  `wait_token_hash` CHAR(64) NOT NULL,
  `approve_action_hash` CHAR(64) NOT NULL,
  `reject_action_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'WAITING',
  `channel_id` VARCHAR(80) NOT NULL DEFAULT '',
  `target` VARCHAR(256) NOT NULL DEFAULT '',
  `request_summary` TEXT NOT NULL,
  `requested_at` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `decided_by` VARCHAR(128) NOT NULL DEFAULT '',
  `decided_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_approval_id` (`approval_id`),
  UNIQUE KEY `uk_workflow_approval_run_node` (`run_id`, `node_id`),
  UNIQUE KEY `uk_workflow_approval_approve_action` (`approve_action_hash`),
  UNIQUE KEY `uk_workflow_approval_reject_action` (`reject_action_hash`),
  KEY `idx_workflow_approval_project_status` (`project_id`, `status`, `requested_at`),
  KEY `idx_workflow_approval_expiry` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable HUMAN_APPROVAL workflow decision ledger';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_signal` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `signal_id` VARCHAR(80) NOT NULL,
  `idempotency_key` VARCHAR(64) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `session_id` VARCHAR(100) NOT NULL DEFAULT '',
  `signal_type` VARCHAR(64) NOT NULL,
  `payload_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_signal_id` (`signal_id`),
  UNIQUE KEY `uk_skill_signal_idempotency` (`idempotency_key`),
  KEY `idx_skill_signal_run` (`project_id`, `run_id`),
  KEY `idx_skill_signal_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill自动进化信号';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_hint` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `hint_id` VARCHAR(80) NOT NULL,
  `signal_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `hint_type` VARCHAR(64) NOT NULL,
  `content_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_hint_id` (`hint_id`),
  KEY `idx_skill_hint_signal` (`signal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Authoring输入Hint';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_patch_candidate` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `candidate_id` VARCHAR(80) NOT NULL,
  `candidate_hash` VARCHAR(64) NOT NULL,
  `source_run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `source_type` VARCHAR(64) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `scope` VARCHAR(24) NOT NULL DEFAULT 'PROJECT',
  `target_skill_id` VARCHAR(128) NOT NULL DEFAULT '',
  `patch_type` VARCHAR(64) NOT NULL,
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'LOW',
  `base_skill_version` INT NOT NULL DEFAULT 0,
  `base_skill_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `context_bundle_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `evidence_refs_json` MEDIUMTEXT NOT NULL,
  `changes_json` MEDIUMTEXT NOT NULL,
  `eval_cases_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
  `reason_code` VARCHAR(128) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_candidate_id` (`candidate_id`),
  UNIQUE KEY `uk_skill_candidate_hash` (`project_id`, `candidate_hash`),
  KEY `idx_skill_candidate_status` (`status`, `create_time`),
  KEY `idx_skill_candidate_target` (`project_id`, `target_skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化Skill Patch Candidate';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_patch_validation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `validation_id` VARCHAR(80) NOT NULL,
  `candidate_id` VARCHAR(80) NOT NULL,
  `validation_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `score` DECIMAL(8,4) NOT NULL DEFAULT 0,
  `result_json` MEDIUMTEXT NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_validation_id` (`validation_id`),
  UNIQUE KEY `uk_skill_candidate_validation` (`candidate_id`, `validation_type`),
  KEY `idx_skill_validation_candidate` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Candidate自动验证';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_release` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `release_id` VARCHAR(80) NOT NULL,
  `candidate_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `target_skill_id` VARCHAR(128) NOT NULL DEFAULT '',
  `status` VARCHAR(32) NOT NULL,
  `canary_percent` INT NOT NULL DEFAULT 0,
  `baseline_version` INT NOT NULL DEFAULT 0,
  `baseline_skill_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `released_version` INT NOT NULL DEFAULT 0,
  `released_skill_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `reason_code` VARCHAR(128) NOT NULL DEFAULT '',
  `metadata_json` MEDIUMTEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_release_id` (`release_id`),
  UNIQUE KEY `uk_skill_release_candidate` (`candidate_id`),
  KEY `idx_skill_release_status` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Shadow/Canary/Active发布状态';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_runtime_usage` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `usage_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `run_id` VARCHAR(100) NOT NULL,
  `context_bundle_hash` VARCHAR(128) NOT NULL,
  `skill_id` VARCHAR(128) NOT NULL,
  `skill_version` INT NOT NULL,
  `skill_hash` VARCHAR(128) NOT NULL,
  `used_at_node` VARCHAR(128) NOT NULL DEFAULT '',
  `outcome_json` MEDIUMTEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_runtime_usage` (`run_id`, `skill_id`, `skill_version`, `used_at_node`),
  KEY `idx_skill_usage_skill` (`project_id`, `skill_id`, `skill_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill版本运行使用记录';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_effect_metric` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `metric_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `skill_id` VARCHAR(128) NOT NULL,
  `skill_version` INT NOT NULL,
  `metric_window` VARCHAR(32) NOT NULL DEFAULT 'LIFETIME',
  `used_run_count` BIGINT NOT NULL DEFAULT 0,
  `successful_run_count` BIGINT NOT NULL DEFAULT 0,
  `evidence_sufficient_count` BIGINT NOT NULL DEFAULT 0,
  `tool_call_count` BIGINT NOT NULL DEFAULT 0,
  `replan_count` BIGINT NOT NULL DEFAULT 0,
  `blocked_tool_call_count` BIGINT NOT NULL DEFAULT 0,
  `change_package_created_count` BIGINT NOT NULL DEFAULT 0,
  `change_package_approved_count` BIGINT NOT NULL DEFAULT 0,
  `landing_succeeded_count` BIGINT NOT NULL DEFAULT 0,
  `user_negative_feedback_count` BIGINT NOT NULL DEFAULT 0,
  `needs_replan_count` BIGINT NOT NULL DEFAULT 0,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_effect_metric` (`project_id`, `skill_id`, `skill_version`, `metric_window`),
  UNIQUE KEY `uk_skill_metric_id` (`metric_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill版本效果指标';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_eval_case` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `eval_case_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `source_run_id` VARCHAR(100) NOT NULL DEFAULT '',
  `input_json` MEDIUMTEXT NOT NULL,
  `expected_json` MEDIUMTEXT NOT NULL,
  `evidence_refs_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_eval_case_id` (`eval_case_id`),
  KEY `idx_skill_eval_project` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Shadow回归评估样例';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_hidden_eval_suite` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `suite_id` VARCHAR(96) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `skill_id` VARCHAR(128) NOT NULL,
  `base_version` BIGINT NOT NULL,
  `base_skill_hash` VARCHAR(64) NOT NULL,
  `suite_version` VARCHAR(64) NOT NULL,
  `hidden_cases_json` MEDIUMTEXT NOT NULL,
  `mutation_cases_json` MEDIUMTEXT NOT NULL,
  `hidden_eval_hash` VARCHAR(64) NOT NULL,
  `mutation_eval_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `actor` VARCHAR(128) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_hidden_suite_id` (`suite_id`),
  UNIQUE KEY `uk_skill_hidden_suite_target`
    (`project_id`, `skill_id`, `base_version`, `base_skill_hash`, `suite_version`),
  KEY `idx_skill_hidden_suite_lookup`
    (`project_id`, `skill_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Strict Tournament 隐藏与变异评估套件';
