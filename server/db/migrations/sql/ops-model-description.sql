-- AI 客户端模型说明字段增量迁移
-- 前端模型目录已经支持维护 description；本迁移补齐后端真实持久化字段。

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

CALL add_column_if_missing('ai_client_model', 'description', '`description` varchar(512) NOT NULL DEFAULT '''' COMMENT ''模型说明'' AFTER `model_usage`');
DROP PROCEDURE IF EXISTS add_column_if_missing;

UPDATE ai_client_model
SET description = model_usage
WHERE description IS NULL OR description = '';
