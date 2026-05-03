-- OPS-05: additive, session-level task episodes. Existing skill/run episodes retain their meaning.
CREATE TABLE IF NOT EXISTS ai_ops_task_episode_session (
  session_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  active_episode_id VARCHAR(80) NOT NULL DEFAULT '',
  assigned_through_seq BIGINT NOT NULL DEFAULT 0,
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_task_episode_context (
  run_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(80) NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  snapshot_hash CHAR(64) NOT NULL,
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_task_episode (
  episode_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(80) NOT NULL,
  goal TEXT NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  outcome VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
  verified_outcome_ref VARCHAR(160) NOT NULL DEFAULT '',
  last_consolidated_revision BIGINT NOT NULL DEFAULT 0,
  last_activity_ms BIGINT NOT NULL,
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_task_episode_scope(project_id, session_id, last_activity_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_task_episode_turn (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(80) NOT NULL,
  turn_seq BIGINT NOT NULL,
  end_seq BIGINT NOT NULL DEFAULT 0,
  source_run_ref VARCHAR(80) NOT NULL,
  episode_id VARCHAR(80) NOT NULL DEFAULT '',
  episode_revision BIGINT NOT NULL DEFAULT 0,
  classifier_revision VARCHAR(80) NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'WAITING_TURN',
  input_json MEDIUMTEXT NULL,
  input_hash CHAR(64) NOT NULL DEFAULT '',
  context_fidelity VARCHAR(48) NOT NULL DEFAULT '',
  decision_json TEXT NULL,
  attempts INT NOT NULL DEFAULT 0,
  lease_owner VARCHAR(64) NOT NULL DEFAULT '',
  epoch BIGINT NOT NULL DEFAULT 0,
  lease_until_ms BIGINT NOT NULL DEFAULT 0,
  next_attempt_ms BIGINT NOT NULL DEFAULT 0,
  last_error VARCHAR(120) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_task_episode_turn(session_id, turn_seq),
  UNIQUE KEY uk_task_episode_source(session_id, source_run_ref),
  KEY idx_task_episode_pending(status, next_attempt_ms, lease_until_ms),
  KEY idx_task_episode_members(episode_id, episode_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_episode_consolidation_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  episode_id VARCHAR(80) NOT NULL,
  revision BIGINT NOT NULL,
  artifact_type VARCHAR(32) NOT NULL,
  generator_hash CHAR(64) NOT NULL,
  trigger_reasons VARCHAR(240) NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  input_json MEDIUMTEXT NOT NULL,
  input_hash CHAR(64) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  lease_owner VARCHAR(64) NOT NULL DEFAULT '',
  epoch BIGINT NOT NULL DEFAULT 0,
  lease_until_ms BIGINT NOT NULL DEFAULT 0,
  next_attempt_ms BIGINT NOT NULL DEFAULT 0,
  last_error VARCHAR(120) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_episode_consolidation(episode_id, revision, artifact_type, generator_hash),
  KEY idx_episode_consolidation_pending(status, next_attempt_ms, lease_until_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_episode_artifact (
  episode_id VARCHAR(80) NOT NULL,
  revision BIGINT NOT NULL,
  artifact_type VARCHAR(32) NOT NULL,
  generator_hash CHAR(64) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  input_hash CHAR(64) NOT NULL,
  outcome VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(episode_id, revision, artifact_type, generator_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
