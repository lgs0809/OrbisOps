-- Phase 068: Completion Projection downstream idempotency.
-- The Outbox is at-least-once. A crash after the downstream write but before
-- Outbox acknowledgement must not create duplicate Work Session checkpoints.

DROP PROCEDURE IF EXISTS ops_completion_projection_add_column;
DELIMITER $$
CREATE PROCEDURE ops_completion_projection_add_column(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS ops_completion_projection_add_index;
DELIMITER $$
CREATE PROCEDURE ops_completion_projection_add_index(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_completion_projection_add_column(
  'ai_ops_agent_run_checkpoint',
  'delivery_key',
  '`delivery_key` VARCHAR(160) NULL COMMENT ''Outbox projection/channel idempotency key'' AFTER `checkpoint_hash`'
);

CALL ops_completion_projection_add_index(
  'ai_ops_agent_run_checkpoint',
  'uk_checkpoint_delivery',
  'UNIQUE KEY `uk_checkpoint_delivery` (`run_id`, `project_id`, `delivery_key`)'
);

DROP PROCEDURE IF EXISTS ops_completion_projection_add_index;
DROP PROCEDURE IF EXISTS ops_completion_projection_add_column;
