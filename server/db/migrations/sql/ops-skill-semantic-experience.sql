CREATE TABLE IF NOT EXISTS ai_ops_skill_method_experience (
  source_id VARCHAR(80) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  run_id VARCHAR(100) NOT NULL,
  episode_id VARCHAR(80) NOT NULL,
  episode_revision BIGINT NOT NULL,
  source_hash VARCHAR(64) NOT NULL,
  method_json MEDIUMTEXT NOT NULL,
  method_hash VARCHAR(64) NOT NULL,
  extraction_audit MEDIUMTEXT NOT NULL,
  group_id VARCHAR(64) NOT NULL DEFAULT '',
  status VARCHAR(24) NOT NULL DEFAULT 'EXTRACTED',
  last_decision MEDIUMTEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_method_group(project_id,group_id,status),
  KEY idx_method_episode(project_id,episode_id,episode_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_skill_method_group (
  group_id VARCHAR(64) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  method_json MEDIUMTEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL DEFAULT '',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_method_group_scope(project_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_skill_method_group_version (
  group_id VARCHAR(64) NOT NULL,
  version BIGINT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  method_json MEDIUMTEXT NOT NULL,
  sources_json MEDIUMTEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(group_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
