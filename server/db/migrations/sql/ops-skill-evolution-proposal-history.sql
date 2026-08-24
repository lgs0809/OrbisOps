-- Preserve invalidated, unpublished author input before rereading a changed baseline.
-- Published/candidate-linked proposals remain in the authoritative proposal table.
CREATE TABLE IF NOT EXISTS ai_ops_skill_evolution_proposal_history (
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
  create_time TIMESTAMP(3) NOT NULL,
  superseded_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  superseded_reason VARCHAR(80) NOT NULL,
  superseded_epoch BIGINT NOT NULL,
  KEY idx_proposal_history_job(job_id,source_id,superseded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
