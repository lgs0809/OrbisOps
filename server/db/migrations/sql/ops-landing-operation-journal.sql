-- Upgrade landing operation audit rows into a pre-dispatch operation journal.

DROP PROCEDURE IF EXISTS add_ai_ops_operation_journal_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_operation_journal_column(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
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

CALL add_ai_ops_operation_journal_column('execution_key', '`execution_key` VARCHAR(160) NOT NULL DEFAULT '''' AFTER `operation_hash`');
CALL add_ai_ops_operation_journal_column('fact_status', '`fact_status` VARCHAR(32) NOT NULL DEFAULT ''NONE'' AFTER `status`');
CALL add_ai_ops_operation_journal_column('dispatch_attempts', '`dispatch_attempts` INT NOT NULL DEFAULT 0 AFTER `fact_status`');
CALL add_ai_ops_operation_journal_column('claimed_at', '`claimed_at` TIMESTAMP NULL DEFAULT NULL AFTER `dispatch_attempts`');
CALL add_ai_ops_operation_journal_column('update_time', '`update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `create_time`');

UPDATE `ai_ops_change_package_landing_operation_run`
SET `execution_key` = CONCAT('legacy:', `operation_run_id`)
WHERE `execution_key` IS NULL OR `execution_key` = '';

ALTER TABLE `ai_ops_change_package_landing_operation_run`
  MODIFY COLUMN `finished_at` TIMESTAMP NULL DEFAULT NULL;

DROP PROCEDURE IF EXISTS add_ai_ops_operation_journal_index;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_operation_journal_index()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_change_package_landing_operation_run'
      AND INDEX_NAME = 'uk_operation_execution_key'
  ) THEN
    ALTER TABLE `ai_ops_change_package_landing_operation_run`
      ADD UNIQUE KEY `uk_operation_execution_key` (`execution_key`);
  END IF;
END$$
DELIMITER ;

CALL add_ai_ops_operation_journal_index();

DROP PROCEDURE IF EXISTS add_ai_ops_operation_journal_column;
DROP PROCEDURE IF EXISTS add_ai_ops_operation_journal_index;
