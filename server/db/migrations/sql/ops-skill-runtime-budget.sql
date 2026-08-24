-- Unique version-bound loaded body/resource accounting; no application business state is changed.
CREATE TABLE IF NOT EXISTS ai_ops_skill_runtime_budget (
  project_id VARCHAR(128) NOT NULL,
  run_id VARCHAR(200) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (project_id,run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS ai_ops_skill_runtime_budget_item (
  project_id VARCHAR(128) NOT NULL,
  run_id VARCHAR(200) NOT NULL,
  skill_key VARCHAR(512) NOT NULL,
  item_hash CHAR(64) NOT NULL,
  units INT NOT NULL,
  loaded_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (project_id,run_id,item_hash),
  CONSTRAINT chk_skill_runtime_budget_units CHECK (units >= 0 AND units <= 6000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
