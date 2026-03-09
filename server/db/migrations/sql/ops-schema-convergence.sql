-- Converge databases that applied historical migrations before their latest schema
-- definitions were introduced. This migration is intentionally forward-only: it
-- repairs the installed schema without rewriting migration history.

DROP PROCEDURE IF EXISTS ops_schema_add_column_055;
DELIMITER $$
CREATE PROCEDURE ops_schema_add_column_055(
  IN p_table_name VARCHAR(64),
  IN p_column_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS ops_schema_add_index_055;
DELIMITER $$
CREATE PROCEDURE ops_schema_add_index_055(
  IN p_table_name VARCHAR(64),
  IN p_index_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND INDEX_NAME = p_index_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- Scheduled tasks must have a durable project boundary. Legacy rows that cannot be
-- attributed are disabled rather than being executed with an empty project.
CALL ops_schema_add_column_055(
  'ai_agent_task_schedule',
  'project_id',
  'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `id`'
);

UPDATE `ai_agent_task_schedule`
SET `project_id` = JSON_UNQUOTE(JSON_EXTRACT(`task_param`, '$.projectId'))
WHERE (`project_id` IS NULL OR `project_id` = '')
  AND JSON_VALID(`task_param`)
  AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(`task_param`, '$.projectId')), '') IS NOT NULL;

UPDATE `ai_agent_task_schedule`
SET `project_id` = '__UNASSIGNED__', `status` = 0
WHERE `project_id` IS NULL OR `project_id` = '';

CALL ops_schema_add_index_055(
  'ai_agent_task_schedule',
  'idx_task_project_time',
  'KEY `idx_task_project_time` (`project_id`, `create_time`)'
);

-- Durable Pre-Approval Work Session identity, lease and immutable run manifest.
CALL ops_schema_add_column_055('ai_ops_agent_run', 'project_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `run_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'session_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `project_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'user_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `session_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'agent_id', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `user_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'agent_version', 'INT NULL AFTER `agent_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'agent_definition_hash', 'VARCHAR(64) NOT NULL DEFAULT '''' AFTER `agent_version`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'execution_harness', 'VARCHAR(48) NOT NULL DEFAULT ''PROJECT_PRE_APPROVAL'' AFTER `agent_definition_hash`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'current_attempt_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `status`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'state_version', 'BIGINT NOT NULL DEFAULT 0 AFTER `current_attempt_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'fencing_token', 'BIGINT NOT NULL DEFAULT 0 AFTER `state_version`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'worker_id', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `fencing_token`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'lease_token', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `worker_id`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'lease_expires_at', 'DATETIME(3) NULL AFTER `lease_token`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'cancel_requested', 'TINYINT NOT NULL DEFAULT 0 AFTER `lease_expires_at`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'cancel_requested_by', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `cancel_requested`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'cancel_reason', 'VARCHAR(512) NOT NULL DEFAULT '''' AFTER `cancel_requested_by`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'run_manifest_json', 'MEDIUMTEXT NULL AFTER `cancel_reason`');
CALL ops_schema_add_column_055('ai_ops_agent_run', 'run_manifest_hash', 'VARCHAR(64) NOT NULL DEFAULT '''' AFTER `run_manifest_json`');

-- A pre-upgrade RUNNING row has no authoritative project/agent/manifest identity.
-- It must never be resumed under the durable runtime.
UPDATE `ai_ops_agent_run`
SET `status` = 'FAILED',
    `error_message` = 'LEGACY_RUN_IDENTITY_INCOMPLETE',
    `lease_token` = '',
    `worker_id` = '',
    `lease_expires_at` = NULL,
    `updated_at` = DATE_FORMAT(NOW(3), '%Y-%m-%dT%H:%i:%s.%f')
WHERE `status` IN ('PENDING', 'RUNNING')
  AND (`project_id` = '' OR `run_manifest_hash` = '');

CALL ops_schema_add_index_055(
  'ai_ops_agent_run',
  'idx_run_project_status',
  'KEY `idx_run_project_status` (`project_id`, `status`)'
);
CALL ops_schema_add_index_055(
  'ai_ops_agent_run',
  'idx_run_lease',
  'KEY `idx_run_lease` (`status`, `lease_expires_at`)'
);

CALL ops_schema_add_column_055(
  'ai_ops_chat_session',
  'state_version',
  'BIGINT NOT NULL DEFAULT 1 AFTER `status`'
);

-- Normalize historical process-local sequence values before adding the durable
-- replay-order uniqueness constraint.
DROP PROCEDURE IF EXISTS ops_schema_normalize_trace_055;
DELIMITER $$
CREATE PROCEDURE ops_schema_normalize_trace_055()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_agent_node_trace'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_agent_node_trace'
      AND INDEX_NAME = 'uk_run_sequence'
  ) THEN
    UPDATE `ai_ops_agent_node_trace` AS target_row
    JOIN (
      SELECT ranked.id, ranked.normalized_sequence
      FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY run_id ORDER BY sequence_no, id) AS normalized_sequence
        FROM `ai_ops_agent_node_trace`
        WHERE run_id IS NOT NULL AND run_id <> ''
      ) AS ranked
    ) AS source_row ON source_row.id = target_row.id
    SET target_row.sequence_no = source_row.normalized_sequence;
  END IF;
END$$
DELIMITER ;

CALL ops_schema_normalize_trace_055();
CALL ops_schema_add_index_055(
  'ai_ops_agent_node_trace',
  'uk_run_sequence',
  'UNIQUE KEY `uk_run_sequence` (`run_id`, `sequence_no`)'
);
ALTER TABLE `ai_ops_agent_node_trace`
  MODIFY COLUMN `event_type` VARCHAR(128) NOT NULL COMMENT '事件类型';

-- Indexes that were historically declared only in mutable baseline SQL files.
CALL ops_schema_add_index_055(
  'ai_ops_alert_trigger_event',
  'idx_alert_event_project_time',
  'KEY `idx_alert_event_project_time` (`project_id`, `create_time`)'
);
CALL ops_schema_add_index_055(
  'ai_ops_approved_validation_script',
  'idx_package_status',
  'KEY `idx_package_status` (`package_id`, `package_version`, `status`)'
);
CALL ops_schema_add_index_055(
  'ai_ops_execution_resource',
  'uk_worker_resource',
  'UNIQUE KEY `uk_worker_resource` (`worker_id`, `resource_id`)'
);
CALL ops_schema_add_index_055(
  'ai_ops_sandbox_session',
  'idx_package_id',
  'KEY `idx_package_id` (`package_id`, `package_version`)'
);

-- Replace the legacy task-scoped lock table with the current package/resource lock
-- schema. The rename is atomic; any duplicate resource key aborts the migration.
DROP PROCEDURE IF EXISTS ops_schema_migrate_resource_lock_055;
DELIMITER $$
CREATE PROCEDURE ops_schema_migrate_resource_lock_055()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_resource_lock'
      AND COLUMN_NAME = 'resource_lock_key'
  ) THEN
    IF EXISTS (
      SELECT 1 FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_resource_lock_legacy_055'
    ) THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'RESOURCE_LOCK_LEGACY_BACKUP_ALREADY_EXISTS';
    END IF;

    DROP TABLE IF EXISTS `ai_ops_change_resource_lock_v055`;
    CREATE TABLE `ai_ops_change_resource_lock_v055` (
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ChangePackage Landing resource lock';

    INSERT INTO `ai_ops_change_resource_lock_v055`
      (`resource_key`, `package_id`, `project_id`, `run_id`, `lease_token`, `status`,
       `lease_expires_at`, `create_time`, `update_time`)
    SELECT COALESCE(NULLIF(`resource_key`, ''), `resource_lock_key`),
           `package_id`, `project_id`, `run_id`, `lease_token`, `status`,
           `lease_expires_at`, `create_time`, `update_time`
    FROM `ai_ops_change_resource_lock`;

    RENAME TABLE
      `ai_ops_change_resource_lock` TO `ai_ops_change_resource_lock_legacy_055`,
      `ai_ops_change_resource_lock_v055` TO `ai_ops_change_resource_lock`;
  END IF;
END$$
DELIMITER ;

CALL ops_schema_migrate_resource_lock_055();

DROP PROCEDURE IF EXISTS ops_schema_migrate_resource_lock_055;
DROP PROCEDURE IF EXISTS ops_schema_normalize_trace_055;
DROP PROCEDURE IF EXISTS ops_schema_add_column_055;
DROP PROCEDURE IF EXISTS ops_schema_add_index_055;
