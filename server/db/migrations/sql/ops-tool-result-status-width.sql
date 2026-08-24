-- OPS-03: MCP import legitimately returns DISCOVERED_PENDING_REVIEW (25 characters).
-- Preserve the semantic status and all existing rows; widen only when needed.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='ai_ops_tool_result' AND column_name='status' AND character_maximum_length < 64),
  'ALTER TABLE ai_ops_tool_result MODIFY COLUMN status VARCHAR(64) NOT NULL DEFAULT ''SUCCEEDED''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
