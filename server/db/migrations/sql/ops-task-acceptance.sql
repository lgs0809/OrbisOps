-- OPS-06: additive authoritative acceptance and internal contribution provenance.
-- Existing completed/SUCCEEDED skill observations are retained; no success is backfilled.
CREATE TABLE IF NOT EXISTS ai_ops_task_acceptance (
  acceptance_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  episode_id VARCHAR(80) NOT NULL,
  episode_revision BIGINT NOT NULL,
  request_id VARCHAR(80) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  condition_key CHAR(64) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  source_run_id VARCHAR(80) NOT NULL,
  record_json MEDIUMTEXT NOT NULL,
  record_hash CHAR(64) NOT NULL,
  created_by VARCHAR(128) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_task_acceptance_request(project_id,episode_id,request_id),
  KEY idx_task_acceptance_episode(project_id,episode_id,episode_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_skill_verified_contribution (
  observation_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  agent_id VARCHAR(128) NOT NULL,
  cluster_key CHAR(64) NOT NULL,
  run_id VARCHAR(100) NOT NULL,
  task_episode_id VARCHAR(80) NOT NULL,
  task_revision BIGINT NOT NULL,
  acceptance_id VARCHAR(80) NOT NULL,
  condition_key CHAR(64) NOT NULL,
  observation_type VARCHAR(64) NOT NULL,
  content_json MEDIUMTEXT NOT NULL,
  evidence_refs_json MEDIUMTEXT NOT NULL,
  task_template_hash CHAR(64) NOT NULL,
  trajectory_hash CHAR(64) NOT NULL,
  quality_score DECIMAL(8,4) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_verified_contribution_cluster(project_id,agent_id,cluster_key),
  KEY idx_verified_contribution_task(project_id,task_episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
