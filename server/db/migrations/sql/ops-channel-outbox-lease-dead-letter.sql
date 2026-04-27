-- Add crash recovery and finite retry semantics to Channel notification Outbox.

DROP PROCEDURE IF EXISTS ops_channel_outbox_add_column;
DELIMITER $$
CREATE PROCEDURE ops_channel_outbox_add_column(IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='ai_ops_channel_notification_outbox'
      AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_channel_notification_outbox` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_channel_outbox_add_column('lease_expires_at', 'DATETIME NULL AFTER `locked_token`');
CALL ops_channel_outbox_add_column('last_attempt_at', 'DATETIME NULL AFTER `lease_expires_at`');
CALL ops_channel_outbox_add_column('dead_letter_at', 'DATETIME NULL AFTER `last_attempt_at`');

DROP PROCEDURE IF EXISTS ops_channel_outbox_add_column;

-- A task left RUNNING by an old process is not assumed successful. It becomes
-- retryable and will later be capped by the application max-attempt policy.
UPDATE ai_ops_channel_notification_outbox
SET status='FAILED', locked_token='', lease_expires_at=NULL, next_retry_at=NOW(),
    last_error='CHANNEL_OUTBOX_LEASE_MIGRATED', update_time=CURRENT_TIMESTAMP
WHERE status='RUNNING';
