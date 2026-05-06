-- OPS-06: immutable input/result and pending-asset budget on the existing Evolution job.
CREATE TABLE IF NOT EXISTS ai_ops_skill_evolution_proposal (
  plan_id VARCHAR(80) NOT NULL PRIMARY KEY,
  job_id VARCHAR(80) NOT NULL,
  source_id VARCHAR(80) NOT NULL,
  project_id VARCHAR(128) NOT NULL,
  agent_id VARCHAR(128) NOT NULL,
  cluster_key VARCHAR(128) NOT NULL,
  plan_hash CHAR(64) NOT NULL,
  input_json MEDIUMTEXT NOT NULL,
  authored_json MEDIUMTEXT NULL,
  authored_hash CHAR(64) NOT NULL DEFAULT '',
  candidate_id VARCHAR(80) NOT NULL DEFAULT '',
  asset_set_hash CHAR(64) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_proposal_job_source(job_id,source_id),
  KEY idx_proposal_candidate(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_ops_skill_pending_asset_proposal (
  asset_set_hash CHAR(64) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  assets_json TEXT NOT NULL,
  plan_id VARCHAR(80) NOT NULL,
  candidate_id VARCHAR(80) NOT NULL,
  next_proposal_at_ms BIGINT NOT NULL,
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
