-- Immutable creator. Legacy rows stay unresolved until creation audit proves their owner.
SET @ddl = IF(NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='ai_agent_task_schedule' AND column_name='created_by'),
  'ALTER TABLE ai_agent_task_schedule ADD COLUMN created_by VARCHAR(128) NULL COMMENT ''Authenticated schedule creator''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
