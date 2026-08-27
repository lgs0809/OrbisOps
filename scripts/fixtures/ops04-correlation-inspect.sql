-- Read-only inspection. Database session and stored Incident timestamps use UTC.
-- 127.0.0.1:13362 / orbisops_acceptance. Credentials: deploy/.env.acceptance (private).
-- Seed through scripts/seed-alert-correlation.py; do not manufacture successful states in SQL.
SET @project_id = 'ops-acceptance-a';

SELECT s.project_id, s.environment, t.revision, t.updated_by, t.updated_at, t.edges_json
FROM ai_ops_alert_correlation_scope s JOIN ai_ops_alert_correlation_topology t USING (scope_key)
WHERE s.project_id = @project_id;

-- merged_into retains previous groups. Only NULL groups are shown as current groups.
SELECT g.group_id, g.environment, g.revision, g.merged_into,
       JSON_UNQUOTE(JSON_EXTRACT(g.anchor_json, '$.title')) AS anchor_alert,
       FROM_UNIXTIME(g.anchor_epoch) AS onset_utc, COUNT(m.incident_id) AS members
FROM ai_ops_alert_correlation_group g LEFT JOIN ai_ops_alert_correlation_member m USING (group_id)
WHERE g.project_id = @project_id
GROUP BY g.group_id ORDER BY g.created_at, g.group_id;

SELECT g.group_id, m.incident_id, i.status AS incident_status, m.occurrence_count, m.last_event_id,
       JSON_UNQUOTE(JSON_EXTRACT(m.signal_json, '$.title')) AS alert_title,
       JSON_UNQUOTE(JSON_EXTRACT(m.signal_json, '$.entityId')) AS entity,
       JSON_EXTRACT(m.signal_json, '$.recovery') AS received_recovery_signal,
       m.decision_json
FROM ai_ops_alert_correlation_group g JOIN ai_ops_alert_correlation_member m USING (group_id)
JOIN ai_ops_incident i USING (incident_id)
WHERE g.project_id = @project_id AND g.merged_into IS NULL ORDER BY g.group_id, m.incident_id;

SELECT d.event_id, d.group_id, e.alert_name, e.status AS webhook_status, d.decision_json, d.created_at
FROM ai_ops_alert_correlation_decision d JOIN ai_ops_alert_trigger_event e ON e.id=d.event_id
WHERE e.project_id = @project_id ORDER BY d.event_id;

SELECT r.id, r.group_id, r.action_type, r.detail_json, r.created_at
FROM ai_ops_alert_correlation_revision r JOIN ai_ops_alert_correlation_group g USING (group_id)
WHERE g.project_id = @project_id ORDER BY r.id;

-- Outbox FIRST is deduplicated per identical alert; different symptoms retain separate investigations.
SELECT o.id, o.fingerprint, o.event_type, o.status, o.run_id, o.retry_count, o.error_message
FROM ai_ops_alert_trigger_outbox o WHERE o.project_id = @project_id AND o.fingerprint LIKE 'correlation-%'
ORDER BY o.id;

SELECT a.run_id, a.status, a.agent_version, a.agent_definition_hash,
       COUNT(DISTINCT t.result_id) AS remote_receipts
FROM ai_ops_agent_run a JOIN ai_ops_alert_trigger_outbox o ON o.run_id=a.run_id
LEFT JOIN ai_ops_tool_result t ON t.run_id=a.run_id AND t.source='MCP_REMOTE_TOOL'
WHERE o.project_id = @project_id AND o.fingerprint LIKE 'correlation-%'
GROUP BY a.run_id ORDER BY a.run_id;

-- The SQL shows IDs/hashes, not credentials or arbitrary full tool output.
SELECT t.run_id, t.result_id, t.tool_name, t.source, t.status, t.full_output_ref, t.output_hash
FROM ai_ops_tool_result t JOIN ai_ops_alert_trigger_outbox o ON o.run_id=t.run_id
WHERE o.project_id = @project_id AND o.fingerprint LIKE 'correlation-%'
ORDER BY t.id;
