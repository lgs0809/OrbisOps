-- Persist the immutable approved operation required by UNKNOWN reconciliation.

DROP PROCEDURE IF EXISTS ops_landing_recovery_add_column;
DELIMITER $$
CREATE PROCEDURE ops_landing_recovery_add_column(IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='ai_ops_change_package_landing_operation_run'
      AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_change_package_landing_operation_run` ADD COLUMN `',
                      p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_landing_recovery_add_column(
  'operation_snapshot_json',
  'LONGTEXT NULL COMMENT ''Immutable approved operation used only for reconciliation'' AFTER `request_hash`'
);

DROP PROCEDURE IF EXISTS ops_landing_recovery_add_column;
