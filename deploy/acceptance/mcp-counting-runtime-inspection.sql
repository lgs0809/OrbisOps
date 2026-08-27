-- Read-only checks for the actual 2026-11-02 browser Run in orbisops_acceptance.
-- Pass the database name to mysql; this file never changes state.
SELECT version, description, checksum, applied_at
FROM orbisops_schema_history WHERE version IN ('102', '103', '104') ORDER BY version;

SELECT run_id, project_id, status, error_message
FROM ai_ops_agent_run
WHERE run_id='chat-chat-session-237ed809-3b6a-48e5-a432-34c900fb4b64-95bf6aca';

SELECT s.run_id, s.discovery_mode, s.tool_count, s.summary_tokens, s.tokenizer,
       s.counting_basis_json,
       JSON_EXTRACT(t.payload_json, '$.countingBasis')=s.counting_basis_json AS event_basis_matches
FROM ai_ops_mcp_discovery_selection s
JOIN ai_ops_agent_node_trace t ON t.run_id=s.run_id AND t.event_type='MCP_DISCOVERY_MODE'
WHERE s.run_id='chat-chat-session-237ed809-3b6a-48e5-a432-34c900fb4b64-95bf6aca';

SELECT call_id, tool_name, read_only, status
FROM ai_ops_mcp_tool_call
WHERE run_id='chat-chat-session-237ed809-3b6a-48e5-a432-34c900fb4b64-95bf6aca';

SELECT result_id, status, output_hash, SHA2(full_output,256)=output_hash AS receipt_hash_matches
FROM ai_ops_tool_result
WHERE source='MCP_REMOTE_TOOL'
  AND run_id='chat-chat-session-237ed809-3b6a-48e5-a432-34c900fb4b64-95bf6aca';
