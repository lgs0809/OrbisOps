-- Cross-instance alert aggregation, bounded project queues and priority dispatch.

CREATE TABLE IF NOT EXISTS `ai_ops_alert_aggregate` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `aggregate_key` VARCHAR(128) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `rule_id` BIGINT NOT NULL,
  `fingerprint` VARCHAR(160) NOT NULL,
  `current_state` VARCHAR(24) NOT NULL DEFAULT 'FIRING',
  `severity` VARCHAR(64) NOT NULL DEFAULT 'WARNING',
  `severity_rank` INT NOT NULL DEFAULT 50,
  `occurrence_count` BIGINT NOT NULL DEFAULT 1,
  `pending_summary_count` INT NOT NULL DEFAULT 0,
  `affected_resources_json` TEXT NULL,
  `payload_json` MEDIUMTEXT NULL,
  `first_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `next_summary_at` TIMESTAMP NULL,
  `max_summary_at` TIMESTAMP NULL,
  `summary_claim_token` VARCHAR(64) NULL,
  `summary_claimed_at` TIMESTAMP NULL,
  `last_dispatched_at` TIMESTAMP NULL,
  `last_dispatch_type` VARCHAR(32) NULL,
  `version` BIGINT NOT NULL DEFAULT 1,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_aggregate_key` (`aggregate_key`),
  KEY `idx_alert_aggregate_due` (`current_state`, `next_summary_at`, `severity_rank`),
  KEY `idx_alert_aggregate_claim` (`summary_claimed_at`, `summary_claim_token`),
  KEY `idx_alert_aggregate_project` (`project_id`, `last_seen_at`),
  KEY `idx_alert_aggregate_rule_fingerprint` (`rule_id`, `fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cross-instance alert aggregation and debounce state';

DROP PROCEDURE IF EXISTS ops_alert_concurrency_add_column;
DELIMITER $$
CREATE PROCEDURE ops_alert_concurrency_add_column(IN p_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_alert_trigger_outbox' AND COLUMN_NAME=p_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_alert_trigger_outbox` ADD COLUMN `', p_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_alert_concurrency_add_column('project_id', 'VARCHAR(80) NOT NULL DEFAULT '''' COMMENT ''Project isolation boundary'' AFTER `rule_id`');
CALL ops_alert_concurrency_add_column('aggregate_key', 'VARCHAR(128) NULL COMMENT ''Alert aggregate key'' AFTER `fingerprint`');
CALL ops_alert_concurrency_add_column('event_type', 'VARCHAR(32) NOT NULL DEFAULT ''FIRST'' COMMENT ''FIRST/ESCALATION/RECOVERY/SUMMARY'' AFTER `aggregate_key`');
CALL ops_alert_concurrency_add_column('priority', 'INT NOT NULL DEFAULT 50 COMMENT ''Dispatch priority'' AFTER `event_type`');

DROP PROCEDURE IF EXISTS ops_alert_concurrency_add_column;

DROP PROCEDURE IF EXISTS ops_alert_concurrency_add_index;
DELIMITER $$
CREATE PROCEDURE ops_alert_concurrency_add_index(IN p_name VARCHAR(64), IN p_columns TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_alert_trigger_outbox' AND INDEX_NAME=p_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_alert_trigger_outbox` ADD INDEX `', p_name, '` (', p_columns, ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_alert_concurrency_add_index('idx_alert_outbox_priority', '`status`, `next_retry_at`, `priority`, `id`');
CALL ops_alert_concurrency_add_index('idx_alert_outbox_project_status', '`project_id`, `status`, `priority`');
CALL ops_alert_concurrency_add_index('idx_alert_outbox_aggregate', '`aggregate_key`, `event_type`');

DROP PROCEDURE IF EXISTS ops_alert_concurrency_add_index;
