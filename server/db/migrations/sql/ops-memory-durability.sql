-- OPS-01. Additive migration. Original messages and audit hashes remain intact.
-- Column/index guards make interruption before manifest recording resumable.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='ai_ops_chat_message' AND column_name='message_seq'), 'SELECT 1',
  'ALTER TABLE ai_ops_chat_message ADD COLUMN message_seq BIGINT NULL, ADD COLUMN project_id VARCHAR(128) NOT NULL DEFAULT '''', ADD COLUMN turn_id VARCHAR(160) NOT NULL DEFAULT '''', ADD COLUMN capture_key CHAR(64) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE ai_ops_chat_message m LEFT JOIN ai_ops_chat_session s ON s.session_id=m.session_id
SET m.message_seq=m.id, m.project_id=COALESCE(s.project_id, '') WHERE m.message_seq IS NULL;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='ai_ops_chat_message' AND index_name='uk_memory_message_seq'), 'SELECT 1',
  'ALTER TABLE ai_ops_chat_message ADD UNIQUE KEY uk_memory_message_seq(session_id, message_seq), ADD UNIQUE KEY uk_memory_capture_key(session_id, capture_key)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_ops_conversation_memory_state (
  session_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL DEFAULT '',
  user_id VARCHAR(80) NOT NULL DEFAULT '',
  last_seq BIGINT NOT NULL DEFAULT 0,
  covered_seq BIGINT NOT NULL DEFAULT 0,
  summary_revision BIGINT NOT NULL DEFAULT 0,
  summary_content MEDIUMTEXT,
  protected_messages_json MEDIUMTEXT,
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ai_ops_conversation_memory_state(session_id, project_id, user_id, last_seq)
SELECT m.session_id, COALESCE(MAX(s.project_id), ''), COALESCE(MAX(s.user_id), ''), MAX(m.message_seq)
FROM ai_ops_chat_message m LEFT JOIN ai_ops_chat_session s ON s.session_id=m.session_id
GROUP BY m.session_id ON DUPLICATE KEY UPDATE last_seq=GREATEST(last_seq, VALUES(last_seq));

CREATE TABLE IF NOT EXISTS ai_ops_memory_summary_version (
  session_id VARCHAR(80) NOT NULL,
  revision BIGINT NOT NULL,
  project_id VARCHAR(128) NOT NULL DEFAULT '',
  covered_seq BIGINT NOT NULL,
  content MEDIUMTEXT NOT NULL,
  protected_messages_json MEDIUMTEXT NOT NULL,
  source VARCHAR(64) NOT NULL,
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(session_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_memory_post_processing (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(80) NOT NULL,
  message_seq BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  buffer_size INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  lease_token VARCHAR(64) NOT NULL DEFAULT '',
  epoch BIGINT NOT NULL DEFAULT 0,
  lease_until_ms BIGINT NOT NULL DEFAULT 0,
  next_attempt_ms BIGINT NOT NULL DEFAULT 0,
  last_error VARCHAR(200) NOT NULL DEFAULT '',
  result_kind VARCHAR(64) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_memory_post_processing(session_id, message_seq, task_type),
  KEY idx_memory_pending(status, next_attempt_ms, lease_until_ms, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_memory_source (
  idempotency_key CHAR(64) NOT NULL PRIMARY KEY,
  memory_id VARCHAR(128) NOT NULL,
  source_run_id VARCHAR(160) NOT NULL DEFAULT '',
  proof_refs_json MEDIUMTEXT,
  created_by VARCHAR(80) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_memory_source(memory_id, source_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_memory_logical_lock (
  lock_key CHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
