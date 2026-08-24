-- Complete remote tools/list generations. Connection credentials remain in the existing protected configuration.
CREATE TABLE IF NOT EXISTS ai_ops_mcp_remote_catalog (
 identity_hash CHAR(64) NOT NULL PRIMARY KEY,
 project_id VARCHAR(128) NOT NULL,
 server_id VARCHAR(128) NOT NULL,
 generation BIGINT NOT NULL DEFAULT 0,
 content_hash VARCHAR(64) NOT NULL DEFAULT '',
 tools_json LONGTEXT NOT NULL DEFAULT ('[]'),
 checked_at DATETIME(6) NULL,
 succeeded_at DATETIME(6) NULL,
 error_code VARCHAR(128) NOT NULL DEFAULT '',
 KEY idx_remote_catalog_scope (project_id,server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_mcp_remote_catalog_version (
 identity_hash CHAR(64) NOT NULL,
 generation BIGINT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 tools_json LONGTEXT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(identity_hash,generation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
