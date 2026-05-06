-- OPS-06: extend the existing Evolution job; preserve every original job and patch.
CREATE TABLE IF NOT EXISTS ai_ops_skill_evolution_job_state (
  job_id VARCHAR(80) NOT NULL PRIMARY KEY,
  source_id VARCHAR(80) NOT NULL DEFAULT '',
  lease_token VARCHAR(64) NOT NULL DEFAULT '',
  epoch BIGINT NOT NULL DEFAULT 0,
  lease_until_ms BIGINT NOT NULL DEFAULT 0,
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO ai_ops_skill_evolution_job_state(job_id)
  SELECT job_id FROM ai_ops_skill_evolution_job;

CREATE TABLE IF NOT EXISTS ai_ops_skill_evolution_source (
  source_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(80) NOT NULL,
  run_id VARCHAR(80) NOT NULL,
  episode_id VARCHAR(80) NOT NULL,
  episode_revision BIGINT NOT NULL,
  input_json MEDIUMTEXT NOT NULL,
  input_hash CHAR(64) NOT NULL,
  candidate_id VARCHAR(80) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_evolution_source_episode(project_id,episode_id,episode_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
