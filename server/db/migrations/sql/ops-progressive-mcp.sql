CREATE TABLE IF NOT EXISTS `ai_ops_tool_catalog_summary` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` VARCHAR(128) NOT NULL,
  `summary_json` MEDIUMTEXT NOT NULL,
  `tool_count` INT NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目工具目录摘要';

CREATE TABLE IF NOT EXISTS `ai_ops_tool_schema_cache` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `cache_id` VARCHAR(160) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `tool_id` VARCHAR(128) NOT NULL,
  `schema_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cache_id` (`cache_id`),
  KEY `idx_project_tool` (`project_id`, `tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具Schema按需Hydrate缓存';

CREATE TABLE IF NOT EXISTS `ai_ops_tool_routing_decision` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `decision_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `node_id` VARCHAR(128) NOT NULL DEFAULT '',
  `run_id` VARCHAR(80) NOT NULL DEFAULT '',
  `capability` VARCHAR(128) NOT NULL DEFAULT '',
  `request_json` MEDIUMTEXT NULL,
  `selected_tools_json` MEDIUMTEXT NULL,
  `reason` VARCHAR(1000) NOT NULL DEFAULT '',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SELECTED',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_id` (`decision_id`),
  KEY `idx_project_time` (`project_id`, `create_time`),
  KEY `idx_agent_node` (`agent_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渐进式MCP工具路由决策';

CREATE TABLE IF NOT EXISTS `ai_ops_mcp_tool_call` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `call_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `node_id` VARCHAR(128) NOT NULL DEFAULT '',
  `run_id` VARCHAR(80) NOT NULL DEFAULT '',
  `tool_id` VARCHAR(128) NOT NULL,
  `mcp_id` VARCHAR(128) NOT NULL DEFAULT '',
  `tool_name` VARCHAR(256) NOT NULL DEFAULT '',
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'LOW',
  `read_only` TINYINT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `input_json` MEDIUMTEXT NULL,
  `output_json` MEDIUMTEXT NULL,
  `duration_ms` BIGINT DEFAULT NULL,
  `error_message` TEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_call_id` (`call_id`),
  KEY `idx_project_time` (`project_id`, `create_time`),
  KEY `idx_tool_time` (`tool_id`, `create_time`),
  KEY `idx_mcp_id` (`mcp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP工具调用审计';

CREATE TABLE IF NOT EXISTS `ai_ops_mcp_tool_snapshot` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `snapshot_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `mcp_id` VARCHAR(128) NOT NULL,
  `tool_id` VARCHAR(128) NOT NULL DEFAULT '',
  `tool_name` VARCHAR(256) NOT NULL,
  `schema_hash` VARCHAR(128) NOT NULL,
  `schema_json` MEDIUMTEXT NULL,
  `metadata_json` MEDIUMTEXT NULL,
  `metadata_complete` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_mcp_tool_hash` (`project_id`, `mcp_id`, `tool_name`, `schema_hash`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  KEY `idx_project_tool` (`project_id`, `tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目MCP远端工具快照';

CREATE TABLE IF NOT EXISTS `ai_ops_mcp_tool_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `policy_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `mcp_id` VARCHAR(128) NOT NULL,
  `tool_id` VARCHAR(128) NOT NULL DEFAULT '',
  `tool_name` VARCHAR(256) NOT NULL,
  `schema_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `effect_type` VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
  `effect_scope` VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
  `mutability` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  `capability` VARCHAR(64) NOT NULL DEFAULT 'MUTATING',
  `allowed_actions_json` MEDIUMTEXT NULL,
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'HIGH',
  `read_only` TINYINT NOT NULL DEFAULT 0,
  `investigate_allowed` TINYINT NOT NULL DEFAULT 0,
  `prepare_allowed` TINYINT NOT NULL DEFAULT 0,
  `land_allowed` TINYINT NOT NULL DEFAULT 0,
  `requires_approved_package` TINYINT NOT NULL DEFAULT 1,
  `requires_human_approval` TINYINT NOT NULL DEFAULT 1,
  `requires_dry_run` TINYINT NOT NULL DEFAULT 0,
  -- Legacy compatibility only; current runtime authorization no longer reads/writes requires_sandbox.
  `requires_sandbox` TINYINT NOT NULL DEFAULT 0,
  `requires_rollback_plan` TINYINT NOT NULL DEFAULT 1,
  `argument_policy_json` MEDIUMTEXT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED',
  `reviewed_by` VARCHAR(128) NOT NULL DEFAULT '',
  `reviewed_at` TIMESTAMP NULL DEFAULT NULL,
  `suggested_by` VARCHAR(128) NOT NULL DEFAULT '',
  `suggested_at` TIMESTAMP NULL DEFAULT NULL,
  `metadata_json` MEDIUMTEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_id` (`policy_id`),
  KEY `idx_project_tool` (`project_id`, `mcp_id`, `tool_name`),
  KEY `idx_project_status` (`project_id`, `status`, `review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目MCP远端工具执行策略';

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN p_table_name VARCHAR(128), IN p_column_name VARCHAR(128), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_column_if_missing('ai_ops_tool_catalog_summary', 'project_id', '`project_id` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `id`');
CALL add_column_if_missing('ai_ops_tool_catalog_summary', 'summary_json', '`summary_json` MEDIUMTEXT NULL AFTER `project_id`');
CALL add_column_if_missing('ai_ops_tool_catalog_summary', 'tool_count', '`tool_count` INT NOT NULL DEFAULT 0 AFTER `summary_json`');

CALL add_column_if_missing('ai_ops_tool_schema_cache', 'cache_id', '`cache_id` VARCHAR(160) NOT NULL DEFAULT '''' AFTER `id`');
CALL add_column_if_missing('ai_ops_tool_schema_cache', 'tool_id', '`tool_id` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `project_id`');
CALL add_column_if_missing('ai_ops_tool_schema_cache', 'schema_json', '`schema_json` MEDIUMTEXT NULL AFTER `tool_id`');
CALL add_column_if_missing('ai_ops_tool_schema_cache', 'status', '`status` VARCHAR(32) NOT NULL DEFAULT ''ACTIVE'' AFTER `schema_json`');

CALL add_column_if_missing('ai_ops_tool_routing_decision', 'run_id', '`run_id` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `node_id`');
CALL add_column_if_missing('ai_ops_tool_routing_decision', 'capability', '`capability` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `run_id`');
CALL add_column_if_missing('ai_ops_tool_routing_decision', 'request_json', '`request_json` MEDIUMTEXT NULL AFTER `capability`');
CALL add_column_if_missing('ai_ops_tool_routing_decision', 'reason', '`reason` VARCHAR(1000) NOT NULL DEFAULT '''' AFTER `selected_tools_json`');
CALL add_column_if_missing('ai_ops_tool_routing_decision', 'status', '`status` VARCHAR(32) NOT NULL DEFAULT ''SELECTED'' AFTER `reason`');

CALL add_column_if_missing('ai_ops_mcp_tool_call', 'run_id', '`run_id` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `node_id`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'tool_id', '`tool_id` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `run_id`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'mcp_id', '`mcp_id` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `tool_id`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'tool_name', '`tool_name` VARCHAR(256) NOT NULL DEFAULT '''' AFTER `mcp_id`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'risk_level', '`risk_level` VARCHAR(24) NOT NULL DEFAULT ''HIGH'' AFTER `tool_name`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'read_only', '`read_only` TINYINT NOT NULL DEFAULT 0 AFTER `risk_level`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'input_json', '`input_json` MEDIUMTEXT NULL AFTER `status`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'output_json', '`output_json` MEDIUMTEXT NULL AFTER `input_json`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'duration_ms', '`duration_ms` BIGINT DEFAULT NULL AFTER `output_json`');
CALL add_column_if_missing('ai_ops_mcp_tool_call', 'error_message', '`error_message` TEXT NULL AFTER `duration_ms`');

CALL add_column_if_missing('ai_ops_mcp_tool_snapshot', 'tool_id', '`tool_id` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `mcp_id`');
CALL add_column_if_missing('ai_ops_mcp_tool_snapshot', 'schema_json', '`schema_json` MEDIUMTEXT NULL AFTER `schema_hash`');
CALL add_column_if_missing('ai_ops_mcp_tool_snapshot', 'metadata_json', '`metadata_json` MEDIUMTEXT NULL AFTER `schema_json`');
CALL add_column_if_missing('ai_ops_mcp_tool_snapshot', 'metadata_complete', '`metadata_complete` TINYINT NOT NULL DEFAULT 0 AFTER `metadata_json`');

CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'tool_id', '`tool_id` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `mcp_id`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'schema_hash', '`schema_hash` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `tool_name`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'effect_type', '`effect_type` VARCHAR(64) NOT NULL DEFAULT ''UNKNOWN'' AFTER `schema_hash`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'effect_scope', '`effect_scope` VARCHAR(64) NOT NULL DEFAULT ''UNKNOWN'' AFTER `effect_type`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'mutability', '`mutability` VARCHAR(32) NOT NULL DEFAULT ''UNKNOWN'' AFTER `effect_scope`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'capability', '`capability` VARCHAR(64) NOT NULL DEFAULT ''MUTATING'' AFTER `mutability`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'allowed_actions_json', '`allowed_actions_json` MEDIUMTEXT NULL AFTER `capability`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'investigate_allowed', '`investigate_allowed` TINYINT NOT NULL DEFAULT 0 AFTER `read_only`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'prepare_allowed', '`prepare_allowed` TINYINT NOT NULL DEFAULT 0 AFTER `investigate_allowed`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'land_allowed', '`land_allowed` TINYINT NOT NULL DEFAULT 0 AFTER `prepare_allowed`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'requires_approved_package', '`requires_approved_package` TINYINT NOT NULL DEFAULT 1 AFTER `land_allowed`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'requires_human_approval', '`requires_human_approval` TINYINT NOT NULL DEFAULT 1 AFTER `requires_approved_package`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'requires_dry_run', '`requires_dry_run` TINYINT NOT NULL DEFAULT 0 AFTER `requires_human_approval`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'requires_sandbox', '`requires_sandbox` TINYINT NOT NULL DEFAULT 0 AFTER `requires_dry_run`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'requires_rollback_plan', '`requires_rollback_plan` TINYINT NOT NULL DEFAULT 1 AFTER `requires_sandbox`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'argument_policy_json', '`argument_policy_json` MEDIUMTEXT NULL AFTER `requires_rollback_plan`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'review_status', '`review_status` VARCHAR(32) NOT NULL DEFAULT ''UNREVIEWED'' AFTER `status`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'reviewed_by', '`reviewed_by` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `review_status`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'reviewed_at', '`reviewed_at` TIMESTAMP NULL DEFAULT NULL AFTER `reviewed_by`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'suggested_by', '`suggested_by` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `reviewed_at`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'suggested_at', '`suggested_at` TIMESTAMP NULL DEFAULT NULL AFTER `suggested_by`');
CALL add_column_if_missing('ai_ops_mcp_tool_policy', 'metadata_json', '`metadata_json` MEDIUMTEXT NULL AFTER `suggested_at`');

ALTER TABLE `ai_ops_mcp_tool_policy`
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  MODIFY COLUMN `prepare_allowed` TINYINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `read_only` TINYINT NOT NULL DEFAULT 0;

DROP PROCEDURE IF EXISTS add_column_if_missing;
