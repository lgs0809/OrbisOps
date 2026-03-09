-- Channel notification replaces the legacy single-provider Weixin notifier.
-- Existing legacy columns remain untouched, but new runtime code only reads the
-- project-scoped Channel fields below.

DROP PROCEDURE IF EXISTS ops_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE ops_add_column_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ops_column_sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_column_definition);
        PREPARE ops_column_stmt FROM @ops_column_sql;
        EXECUTE ops_column_stmt;
        DEALLOCATE PREPARE ops_column_stmt;
    END IF;
END$$
DELIMITER ;

CALL ops_add_column_if_missing('ai_ops_alert_trigger_rule', 'notification_channel_id', "VARCHAR(80) NULL COMMENT '通知 Channel ID'");
CALL ops_add_column_if_missing('ai_ops_alert_trigger_rule', 'notification_target', "VARCHAR(256) NULL COMMENT '通知目标'");
CALL ops_add_column_if_missing('ai_ops_alert_trigger_rule', 'notify_channel', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否通过 Channel 推送'");

DROP PROCEDURE IF EXISTS ops_add_column_if_missing;

CREATE TABLE IF NOT EXISTS `ai_ops_channel_notification_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `dedup_key` VARCHAR(320) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `channel_id` VARCHAR(80) NOT NULL,
  `target` VARCHAR(256) NOT NULL,
  `analysis_id` VARCHAR(128) NOT NULL DEFAULT '',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `request_json` MEDIUMTEXT NULL,
  `response_json` MEDIUMTEXT NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_at` DATETIME NOT NULL,
  `locked_token` VARCHAR(64) NOT NULL DEFAULT '',
  `last_response` MEDIUMTEXT NULL,
  `last_error` TEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_notification_dedup` (`dedup_key`),
  KEY `idx_channel_notification_retry` (`status`, `next_retry_at`),
  KEY `idx_channel_notification_project` (`project_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel主动通知Outbox';

-- Forward-only cleanup: upgraded installations no longer expose the old
-- provider-specific Weixin MCP or its task flag. Channel delivery remains off
-- until an administrator selects a project Channel and notification target.
DELETE FROM `ai_client_tool_mcp`
WHERE `mcp_id` = '5002' AND `mcp_name` = 'weixin-notify';

UPDATE `ai_agent_task_schedule`
SET `task_param` = JSON_REMOVE(
        JSON_SET(`task_param`, '$.notifyChannel', FALSE),
        '$.notifyWeixin'
    )
WHERE JSON_VALID(`task_param`)
  AND JSON_CONTAINS_PATH(`task_param`, 'one', '$.notifyWeixin');
