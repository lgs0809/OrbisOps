-- Durable Work Session ownership, recovery checkpoints, per-run event ordering and agent definition pinning.

DROP PROCEDURE IF EXISTS ops_work_session_add_column;
DELIMITER $$
CREATE PROCEDURE ops_work_session_add_column(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table_name AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_work_session_add_column('ai_ops_agent_run', 'project_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `run_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'session_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `project_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'user_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `session_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'agent_id', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `user_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'agent_version', 'INT NULL AFTER `agent_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'agent_definition_hash', 'VARCHAR(64) NOT NULL DEFAULT '''' AFTER `agent_version`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'execution_harness', 'VARCHAR(48) NOT NULL DEFAULT ''PROJECT_PRE_APPROVAL'' AFTER `agent_definition_hash`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'current_attempt_id', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `status`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'state_version', 'BIGINT NOT NULL DEFAULT 0 AFTER `current_attempt_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'fencing_token', 'BIGINT NOT NULL DEFAULT 0 AFTER `state_version`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'worker_id', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `fencing_token`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'lease_token', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `worker_id`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'lease_expires_at', 'DATETIME(3) NULL AFTER `lease_token`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'cancel_requested', 'TINYINT NOT NULL DEFAULT 0 AFTER `lease_expires_at`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'cancel_requested_by', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `cancel_requested`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'cancel_reason', 'VARCHAR(512) NOT NULL DEFAULT '''' AFTER `cancel_requested_by`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'run_manifest_json', 'MEDIUMTEXT NULL AFTER `cancel_reason`');
CALL ops_work_session_add_column('ai_ops_agent_run', 'run_manifest_hash', 'VARCHAR(64) NOT NULL DEFAULT '''' AFTER `run_manifest_json`');

CALL ops_work_session_add_column('ai_ops_agent_definition', 'definition_hash', 'VARCHAR(64) NOT NULL DEFAULT '''' AFTER `version`');
CALL ops_work_session_add_column('ai_ops_agent_definition_version', 'definition_hash', 'VARCHAR(64) NOT NULL DEFAULT '''' AFTER `version`');
CALL ops_work_session_add_column('ai_ops_chat_session', 'state_version', 'BIGINT NOT NULL DEFAULT 1 AFTER `status`');

CREATE TABLE IF NOT EXISTS `ai_ops_agent_run_attempt` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `attempt_id` VARCHAR(80) NOT NULL,
  `run_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `worker_id` VARCHAR(128) NOT NULL,
  `lease_token` VARCHAR(80) NOT NULL,
  `fencing_token` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `heartbeat_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `run_manifest_hash` VARCHAR(64) NOT NULL,
  `error_message` TEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_id` (`attempt_id`),
  UNIQUE KEY `uk_run_fencing` (`run_id`, `fencing_token`),
  KEY `idx_attempt_project_status` (`project_id`, `status`),
  KEY `idx_attempt_heartbeat` (`status`, `heartbeat_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Pre-Approval Work Session execution attempts';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_run_checkpoint` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `attempt_id` VARCHAR(80) NOT NULL,
  `checkpoint_seq` BIGINT NOT NULL,
  `checkpoint_type` VARCHAR(64) NOT NULL,
  `checkpoint_json` MEDIUMTEXT NOT NULL,
  `checkpoint_hash` VARCHAR(64) NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_checkpoint_seq` (`run_id`, `checkpoint_seq`),
  KEY `idx_checkpoint_project_run` (`project_id`, `run_id`),
  KEY `idx_checkpoint_attempt` (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable Work Session checkpoints';

DROP PROCEDURE IF EXISTS ops_work_session_add_index;
DELIMITER $$
CREATE PROCEDURE ops_work_session_add_index(IN p_table_name VARCHAR(64), IN p_index_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table_name AND INDEX_NAME=p_index_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_work_session_add_index('ai_ops_agent_run', 'idx_run_project_status', 'KEY `idx_run_project_status` (`project_id`, `status`)');
CALL ops_work_session_add_index('ai_ops_agent_run', 'idx_run_lease', 'KEY `idx_run_lease` (`status`, `lease_expires_at`)');

-- Historical rows used process-local sequence counters, so the same run can contain
-- duplicate values. Re-number them deterministically before enforcing replay order.
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

CALL ops_work_session_add_index('ai_ops_agent_node_trace', 'uk_run_sequence', 'UNIQUE KEY `uk_run_sequence` (`run_id`, `sequence_no`)');

DROP PROCEDURE IF EXISTS ops_work_session_add_column;
DROP PROCEDURE IF EXISTS ops_work_session_add_index;
