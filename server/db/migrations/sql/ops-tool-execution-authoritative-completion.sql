-- Stage 629: authoritative ToolResult/Evidence/idempotency completion and replayable projections.

CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_ledger (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  idempotency_key VARCHAR(200) NOT NULL,
  project_id VARCHAR(128) NOT NULL DEFAULT '',
  run_id VARCHAR(100) NOT NULL DEFAULT '',
  node_id VARCHAR(128) NOT NULL DEFAULT '',
  attempt_no INT NOT NULL DEFAULT 0,
  tool_call_index INT NOT NULL DEFAULT 0,
  input_hash VARCHAR(64) NOT NULL,
  target_hash VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  owner_token VARCHAR(128) NOT NULL DEFAULT '',
  fencing_token BIGINT NOT NULL DEFAULT 1,
  lease_expires_at DATETIME(6) NULL,
  allowed TINYINT NOT NULL DEFAULT 0,
  decision_code VARCHAR(64) NOT NULL DEFAULT '',
  result_id VARCHAR(100) NOT NULL DEFAULT '',
  evidence_id VARCHAR(100) NOT NULL DEFAULT '',
  preview_text MEDIUMTEXT NULL,
  output_hash VARCHAR(64) NOT NULL DEFAULT '',
  truncated TINYINT NOT NULL DEFAULT 0,
  full_output_ref VARCHAR(256) NOT NULL DEFAULT '',
  recorded_input_hash VARCHAR(64) NOT NULL DEFAULT '',
  duration_ms BIGINT NOT NULL DEFAULT 0,
  payload_json MEDIUMTEXT NULL,
  error_code VARCHAR(96) NOT NULL DEFAULT '',
  error_message TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_execution_idempotency (idempotency_key),
  KEY idx_tool_execution_run (project_id, run_id, updated_at),
  KEY idx_tool_execution_status (status, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='工具执行幂等与副作用对账账本';

CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_completion_projection (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  projection_id VARCHAR(96) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  projection_json MEDIUMTEXT NOT NULL,
  checkpoint_status VARCHAR(24) NOT NULL,
  audit_status VARCHAR(24) NOT NULL,
  owner_token VARCHAR(128) NOT NULL DEFAULT '',
  fencing_token BIGINT NOT NULL DEFAULT 0,
  lease_expires_at DATETIME(6) NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL,
  last_error TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_completion_projection (projection_id),
  UNIQUE KEY uk_tool_completion_idempotency (idempotency_key),
  KEY idx_tool_completion_pending
    (checkpoint_status, audit_status, next_attempt_at, updated_at),
  KEY idx_tool_completion_claim
    (next_attempt_at, lease_expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='ToolResult/Evidence 权威完成后的 Checkpoint/Audit 投影对账';

DROP PROCEDURE IF EXISTS ops_tool_completion_add_column;
DELIMITER $$
CREATE PROCEDURE ops_tool_completion_add_column(
  IN p_column_name VARCHAR(64),
  IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema=DATABASE()
       AND table_name='ai_ops_tool_execution_completion_projection'
       AND column_name=p_column_name
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE ai_ops_tool_execution_completion_projection ADD COLUMN ',
      p_column_name, ' ', p_definition);
    PREPARE statement_handle FROM @ddl;
    EXECUTE statement_handle;
    DEALLOCATE PREPARE statement_handle;
  END IF;
END$$
DELIMITER ;

CALL ops_tool_completion_add_column(
  'owner_token', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER audit_status');
CALL ops_tool_completion_add_column(
  'fencing_token', 'BIGINT NOT NULL DEFAULT 0 AFTER owner_token');
CALL ops_tool_completion_add_column(
  'lease_expires_at', 'DATETIME(6) NULL AFTER fencing_token');
DROP PROCEDURE IF EXISTS ops_tool_completion_add_column;

DROP PROCEDURE IF EXISTS ops_tool_completion_add_index;
DELIMITER $$
CREATE PROCEDURE ops_tool_completion_add_index()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND table_name='ai_ops_tool_execution_completion_projection'
       AND index_name='idx_tool_completion_claim'
  ) THEN
    ALTER TABLE ai_ops_tool_execution_completion_projection
      ADD KEY idx_tool_completion_claim
        (next_attempt_at, lease_expires_at, id);
  END IF;
END$$
DELIMITER ;
CALL ops_tool_completion_add_index();
DROP PROCEDURE IF EXISTS ops_tool_completion_add_index;
