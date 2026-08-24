-- Additive only: no completed memory tasks or failed outcomes are reclassified here.
SET @ddl=IF(NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_ops_memory_post_processing')
  OR EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='ai_ops_memory_post_processing' AND column_name='model_input_json'),'SELECT 1',
  'ALTER TABLE ai_ops_memory_post_processing ADD COLUMN model_input_json MEDIUMTEXT NULL, ADD COLUMN model_input_hash CHAR(64) NOT NULL DEFAULT ''''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_ops_background_model_recovery_audit (
  recovery_id CHAR(64) NOT NULL PRIMARY KEY,
  family VARCHAR(32) NOT NULL,
  task_id VARCHAR(160) NOT NULL,
  project_id VARCHAR(128) NOT NULL,
  actor VARCHAR(128) NOT NULL,
  prior_status VARCHAR(40) NOT NULL,
  prior_attempts INT NOT NULL,
  prior_error VARCHAR(200) NOT NULL,
  next_attempt_ms BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_background_model_recovery(project_id,family,task_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
