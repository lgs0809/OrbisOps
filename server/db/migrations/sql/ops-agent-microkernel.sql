-- Controlled Ops built-in microkernel metadata.
-- Agent definition JSON remains the execution source; normalized columns are
-- maintained for governance queries and migration/readiness verification.

SET @schema_name = DATABASE();

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='ai_ops_agentscope_agent' AND COLUMN_NAME='max_depth') = 0,
  'ALTER TABLE ai_ops_agentscope_agent ADD COLUMN max_depth INT NOT NULL DEFAULT 1 COMMENT ''子Agent最大派生深度'' AFTER max_iterations',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='ai_ops_agentscope_agent' AND COLUMN_NAME='role') = 0,
  'ALTER TABLE ai_ops_agentscope_agent ADD COLUMN role VARCHAR(64) NULL COMMENT ''内置窄职责角色'' AFTER max_depth',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='ai_ops_agentscope_agent' AND COLUMN_NAME='allowed_tools_json') = 0,
  'ALTER TABLE ai_ops_agentscope_agent ADD COLUMN allowed_tools_json TEXT NULL COMMENT ''运行时工具白名单'' AFTER role',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
