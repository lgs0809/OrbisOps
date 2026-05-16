-- File import provenance, also serializes concurrent importers. No existing Skill is overwritten.
CREATE TABLE IF NOT EXISTS ai_ops_skill_file_projection (
 scope VARCHAR(24) NOT NULL,
 project_id VARCHAR(128) NOT NULL,
 skill_id VARCHAR(128) NOT NULL,
 source_hash CHAR(64) NOT NULL DEFAULT '',
 projected_version INT NOT NULL DEFAULT 0,
 projected_skill_hash VARCHAR(128) NOT NULL DEFAULT '',
 PRIMARY KEY (scope,project_id,skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
