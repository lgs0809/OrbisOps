-- Immutable discovery-mode selection. Current authorization and contracts are still checked on every use.
CREATE TABLE IF NOT EXISTS ai_ops_mcp_discovery_selection (
  scope_hash CHAR(64) NOT NULL PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL,
  run_id VARCHAR(128) NOT NULL,
  agent_id VARCHAR(128) NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  discovery_mode VARCHAR(16) NOT NULL,
  tool_count INT NOT NULL,
  summary_tokens INT NOT NULL,
  tokenizer VARCHAR(32) NOT NULL,
  catalog_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_mcp_discovery_run(project_id,run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
