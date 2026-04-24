-- Make project ownership explicit for inspections and configuration audit.

SET @task_project_column = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_agent_task_schedule ADD COLUMN project_id VARCHAR(128) NOT NULL DEFAULT '''' AFTER id',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_agent_task_schedule'
    AND COLUMN_NAME = 'project_id'
);
PREPARE task_project_stmt FROM @task_project_column;
EXECUTE task_project_stmt;
DEALLOCATE PREPARE task_project_stmt;

UPDATE ai_agent_task_schedule
SET project_id = JSON_UNQUOTE(JSON_EXTRACT(task_param, '$.projectId'))
WHERE project_id = ''
  AND JSON_VALID(task_param)
  AND JSON_UNQUOTE(JSON_EXTRACT(task_param, '$.projectId')) IS NOT NULL;

SET @task_project_index = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_task_project_time ON ai_agent_task_schedule(project_id, create_time)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_agent_task_schedule'
    AND INDEX_NAME = 'idx_task_project_time'
);
PREPARE task_project_index_stmt FROM @task_project_index;
EXECUTE task_project_index_stmt;
DEALLOCATE PREPARE task_project_index_stmt;

SET @audit_project_column = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_ops_config_audit ADD COLUMN project_id VARCHAR(128) NOT NULL DEFAULT '''' AFTER id',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_config_audit'
    AND COLUMN_NAME = 'project_id'
);
PREPARE audit_project_stmt FROM @audit_project_column;
EXECUTE audit_project_stmt;
DEALLOCATE PREPARE audit_project_stmt;

SET @audit_project_index = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_project_time ON ai_ops_config_audit(project_id, create_time)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_config_audit'
    AND INDEX_NAME = 'idx_project_time'
);
PREPARE audit_project_index_stmt FROM @audit_project_index;
EXECUTE audit_project_index_stmt;
DEALLOCATE PREPARE audit_project_index_stmt;
