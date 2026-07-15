-- Read-only manual inspection of the isolated OrbisOps acceptance database.
-- Run with: scripts/test-mcp-runtime.py's sql helper, or a MySQL client connected
-- to 127.0.0.1:13362 / orbisops_acceptance. Credentials stay in private env files.
-- Rule secrets and complete credentials are deliberately excluded.
SET @project_id = 'ops-acceptance-a';

SELECT id, rule_name, status, project_id, agent_definition_id,
       agent_binding_mode, agent_version, agent_definition_hash, question_template
FROM ai_ops_alert_trigger_rule
WHERE project_id = @project_id AND rule_name LIKE 'OPS-04%';

SELECT id, rule_id, fingerprint, event_type, status, run_id, retry_count,
       locked_at, next_retry_at, error_message, create_time, update_time
FROM ai_ops_alert_trigger_outbox
WHERE project_id = @project_id ORDER BY id;

SELECT aggregate_key, rule_id, fingerprint, current_state, occurrence_count,
       pending_summary_count, last_dispatch_type, next_summary_at, version
FROM ai_ops_alert_aggregate WHERE project_id = @project_id ORDER BY id;

SELECT id, agent_id, task_name, cron_expression, status, task_param
FROM ai_agent_task_schedule WHERE task_name LIKE 'OPS-04%';

SELECT e.id, e.schedule_id, e.trigger_type, e.status, e.started_at, e.ended_at,
       e.error_message, e.input, e.output
FROM ai_agent_task_execution e JOIN ai_agent_task_schedule s ON s.id=e.schedule_id
WHERE s.task_name LIKE 'OPS-04%' ORDER BY e.id;

-- Run/checkpoint IDs from the above records can be opened in the workbench.
SELECT run_id, agent_version, agent_definition_hash, status, fencing_token,
       error_message
FROM ai_ops_agent_run
WHERE run_id LIKE 'task_%' ORDER BY id DESC LIMIT 40;

SELECT service_id, COUNT(*) AS measured_requests, SUM(http_status >= 500) AS errors,
       MIN(observed_at) AS first_request, MAX(observed_at) AS latest_request
FROM ops_acceptance_business_a.ops04_request GROUP BY service_id;
