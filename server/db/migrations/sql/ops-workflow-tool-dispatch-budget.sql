-- Append-only reservations before real MCP tools/call dispatches. No existing run/data is rewritten.
CREATE TABLE IF NOT EXISTS ai_ops_workflow_tool_dispatch (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id VARCHAR(80) NOT NULL,
  project_id VARCHAR(80) NOT NULL,
  node_id VARCHAR(128) NOT NULL DEFAULT '',
  mcp_id VARCHAR(128) NOT NULL DEFAULT '',
  tool_name VARCHAR(128) NOT NULL DEFAULT '',
  logical_call_id VARCHAR(80) NOT NULL,
  physical_attempt INT NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  budget_limit INT NOT NULL,
  definition_hash VARCHAR(64) NOT NULL,
  reserved_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_workflow_tool_dispatch (run_id, logical_call_id, physical_attempt),
  KEY idx_workflow_tool_project (project_id, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
