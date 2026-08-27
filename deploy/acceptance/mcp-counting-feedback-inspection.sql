-- Read-only inspection. Use the isolated orbisops_mcp_counting_20261102 schema
-- for synthetic test records; use a specific real run_id for an actual workflow.
SELECT project_id, run_id, agent_id, node_id, discovery_mode,
       tool_count, summary_tokens, tokenizer, catalog_hash,
       counting_basis_json, created_at
FROM ai_ops_mcp_discovery_selection
ORDER BY created_at DESC, scope_hash LIMIT 100;

-- The stored outcome facts and their projection must agree. Each successful
-- correction changes these counts by a delta, without increasing used_run_count.
SELECT m.project_id, m.skill_id, m.skill_version,
       m.used_run_count, m.successful_run_count, m.evidence_sufficient_count,
       COUNT(u.id) AS actual_usage_rows,
       SUM(COALESCE(JSON_EXTRACT(u.outcome_json, '$.success') = TRUE, FALSE)) AS successful_usage_rows,
       SUM(COALESCE(JSON_EXTRACT(u.outcome_json, '$.evidenceSufficient') = TRUE, FALSE)) AS sufficient_usage_rows
FROM ai_ops_skill_effect_metric m
JOIN ai_ops_skill_runtime_usage u
  ON u.project_id=m.project_id AND u.skill_id=m.skill_id AND u.skill_version=m.skill_version
WHERE m.metric_window='LIFETIME'
GROUP BY m.project_id,m.skill_id,m.skill_version,m.used_run_count,
         m.successful_run_count,m.evidence_sufficient_count;
