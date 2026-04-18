-- Persist every approved rollback attempt in the landing operation journal.

DROP PROCEDURE IF EXISTS add_ai_ops_landing_rollback_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_landing_rollback_column(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
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

CALL add_ai_ops_landing_rollback_column('rollback_status',
  '`rollback_status` VARCHAR(32) NOT NULL DEFAULT '''' AFTER `post_check_result_json`');
CALL add_ai_ops_landing_rollback_column('rollback_result_json',
  '`rollback_result_json` MEDIUMTEXT NULL AFTER `rollback_status`');
CALL add_ai_ops_landing_rollback_column('rollback_at',
  '`rollback_at` TIMESTAMP NULL DEFAULT NULL AFTER `rollback_result_json`');

DROP PROCEDURE IF EXISTS add_ai_ops_landing_rollback_column;
