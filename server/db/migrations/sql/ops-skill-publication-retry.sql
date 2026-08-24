CREATE TABLE IF NOT EXISTS ai_ops_skill_publication_retry (
  candidate_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_run_at TIMESTAMP(3) NOT NULL,
  lease_token VARCHAR(80) NULL,
  lease_until TIMESTAMP(3) NULL,
  last_reason VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_skill_publication_retry_due(status,next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
