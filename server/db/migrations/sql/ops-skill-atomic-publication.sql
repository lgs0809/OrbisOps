CREATE TABLE IF NOT EXISTS ai_ops_skill_atomic_publication (
  candidate_id VARCHAR(128) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  operation VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  plan_json LONGTEXT NOT NULL,
  plan_hash CHAR(64) NOT NULL,
  rollback_actor VARCHAR(128) NOT NULL DEFAULT '',
  rollback_reason VARCHAR(1024) NOT NULL DEFAULT '',
  create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_atomic_project (project_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Immutable lineage also records staged/rolled back targets. Neither path deletes method bodies.
CREATE TABLE IF NOT EXISTS ai_ops_skill_atomic_member (
  candidate_id VARCHAR(128) NOT NULL,
  project_id VARCHAR(128) NOT NULL,
  role VARCHAR(16) NOT NULL,
  skill_id VARCHAR(128) NOT NULL,
  skill_version INT NOT NULL,
  skill_hash CHAR(64) NOT NULL,
  package_hash CHAR(64) NOT NULL,
  PRIMARY KEY (candidate_id,role,skill_id),
  KEY idx_atomic_skill (project_id,skill_id,role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- This is an automatic-recall mapping only; explicit Workflow bindings keep the original Skill.
CREATE TABLE IF NOT EXISTS ai_ops_skill_atomic_replacement (
  project_id VARCHAR(128) NOT NULL,
  source_skill_id VARCHAR(128) NOT NULL,
  candidate_id VARCHAR(128) NOT NULL,
  PRIMARY KEY (project_id,source_skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
