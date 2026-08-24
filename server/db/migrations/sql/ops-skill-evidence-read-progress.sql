-- Navigation references only. Original evidence and all model/approval outcomes stay authoritative.
CREATE TABLE IF NOT EXISTS ai_ops_skill_evidence_read_progress (
  progress_key VARCHAR(64) NOT NULL,
  source_hash VARCHAR(64) NOT NULL,
  read_refs_json MEDIUMTEXT NOT NULL,
  read_count INT NOT NULL DEFAULT 0,
  row_version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (progress_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
