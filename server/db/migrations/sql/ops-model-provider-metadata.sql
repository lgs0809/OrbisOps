DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN p_table_name VARCHAR(128), IN p_column_name VARCHAR(128), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_column_if_missing('ai_client_api', 'provider_name', '`provider_name` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''Provider 名称'' AFTER `api_id`');
CALL add_column_if_missing('ai_client_api', 'provider_type', '`provider_type` VARCHAR(64) NOT NULL DEFAULT ''OPENAI_COMPATIBLE'' COMMENT ''Provider 类型'' AFTER `provider_name`');
DROP PROCEDURE IF EXISTS add_column_if_missing;

UPDATE `ai_client_api`
SET `provider_name` = CASE
        WHEN `provider_name` = '' THEN `api_id`
        ELSE `provider_name`
    END,
    `provider_type` = CASE
        WHEN `provider_type` = '' THEN 'OPENAI_COMPATIBLE'
        ELSE `provider_type`
    END;
