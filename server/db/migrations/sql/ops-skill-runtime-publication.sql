-- Runtime pointer is separate from the editable catalog head. Existing rows are not modified.
CREATE TABLE IF NOT EXISTS ai_ops_skill_runtime_publication (
  scope VARCHAR(24) NOT NULL,
  project_id VARCHAR(128) NOT NULL DEFAULT '',
  skill_id VARCHAR(128) NOT NULL,
  model_identity_hash VARCHAR(64) NOT NULL,
  model_identity TEXT NOT NULL,
  skill_version INT NOT NULL,
  skill_hash VARCHAR(128) NOT NULL,
  package_hash VARCHAR(128) NOT NULL,
  generation_id VARCHAR(64) NOT NULL,
  metadata_json MEDIUMTEXT NOT NULL,
  metadata_hash VARCHAR(64) NOT NULL,
  published_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(scope,project_id,skill_id,model_identity_hash),
  KEY idx_runtime_publication_scope(project_id,scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
