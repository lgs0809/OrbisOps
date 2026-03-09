-- Durable, bounded alert outbox ownership and dead-letter state.

DROP PROCEDURE IF EXISTS ops_alert_outbox_add_column;
DELIMITER $$
CREATE PROCEDURE ops_alert_outbox_add_column(IN p_name VARCHAR(64), IN p_definition TEXT)
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

CALL ops_alert_outbox_add_column('locked_at', 'TIMESTAMP NULL COMMENT ''Outbox claim timestamp'' AFTER `locked_token`');
CALL ops_alert_outbox_add_column('dead_letter_at', 'TIMESTAMP NULL COMMENT ''Dead letter timestamp'' AFTER `locked_at`');

DROP PROCEDURE IF EXISTS ops_alert_outbox_add_column;
