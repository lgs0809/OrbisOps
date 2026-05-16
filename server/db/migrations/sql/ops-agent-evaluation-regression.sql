-- Baseline/candidate regression evidence for Agent release gates.
DROP PROCEDURE IF EXISTS ops_agent_eval_add_column;
DELIMITER //
CREATE PROCEDURE ops_agent_eval_add_column(IN p_column_name VARCHAR(128), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_agent_eval_run'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_agent_eval_run` ADD COLUMN ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL ops_agent_eval_add_column('baseline_version',
  '`baseline_version` INT NOT NULL DEFAULT 0 AFTER `definition_hash`');
CALL ops_agent_eval_add_column('baseline_definition_hash',
  '`baseline_definition_hash` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `baseline_version`');
CALL ops_agent_eval_add_column('regression_status',
  '`regression_status` VARCHAR(32) NOT NULL DEFAULT ''PENDING'' AFTER `baseline_definition_hash`');
CALL ops_agent_eval_add_column('baseline_result_json',
  '`baseline_result_json` MEDIUMTEXT NULL AFTER `result_json`');

DROP PROCEDURE IF EXISTS ops_agent_eval_add_column;
