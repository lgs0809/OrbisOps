-- Read-only. Reuse a session created through the actual UI/API; never manufacture accepted tasks with SQL.
SET @ops06_project = 'ops-acceptance-a';
SET @ops06_session = 'REPLACE_WITH_SESSION_ID';
SELECT r.run_id,r.status,r.agent_id,r.agent_version FROM ai_ops_agent_run r
WHERE r.project_id=@ops06_project AND r.session_id=@ops06_session ORDER BY r.id;
SELECT t.turn_seq,t.source_run_ref,t.status,t.episode_id,t.episode_revision,t.attempts,t.last_error
FROM ai_ops_task_episode_turn t WHERE t.project_id=@ops06_project AND t.session_id=@ops06_session ORDER BY t.turn_seq;
SELECT e.episode_id,e.goal,e.revision,e.outcome,e.verified_outcome_ref
FROM ai_ops_task_episode e WHERE e.project_id=@ops06_project AND e.session_id=@ops06_session;
SELECT a.acceptance_id,a.episode_id,a.episode_revision,a.request_id,a.outcome,a.source_run_id,a.condition_key,
       a.record_hash=(SHA2(a.record_json,256)) AS retained_record_hash_matches,a.record_json
FROM ai_ops_task_acceptance a JOIN ai_ops_task_episode e ON e.episode_id=a.episode_id AND e.project_id=a.project_id
WHERE e.project_id=@ops06_project AND e.session_id=@ops06_session ORDER BY a.created_at;
SELECT r.result_id,r.run_id,r.tool_name,r.status,r.output_hash,
       r.output_hash=(SHA2(r.full_output,256)) AS retained_receipt_hash_matches,
       JSON_EXTRACT(r.full_output,'$.normalizedContent') AS observed_result
FROM ai_ops_tool_result r JOIN ai_ops_agent_run run ON run.run_id=r.run_id AND run.project_id=r.project_id
WHERE run.project_id=@ops06_project AND run.session_id=@ops06_session AND r.source='MCP_REMOTE_TOOL' ORDER BY r.id;
-- Historical observations are audit facts, not authoritative successful-source counts.
SELECT outcome,COUNT(*) AS legacy_observations FROM ai_ops_skill_observation
WHERE project_id=@ops06_project GROUP BY outcome;
SELECT c.observation_id,c.task_episode_id,c.task_revision,c.acceptance_id,c.condition_key,e.revision AS current_revision,
       e.outcome AS current_outcome,e.verified_outcome_ref=c.acceptance_id AS current_acceptance
FROM ai_ops_skill_verified_contribution c JOIN ai_ops_task_episode e ON e.episode_id=c.task_episode_id AND e.project_id=c.project_id
WHERE c.project_id=@ops06_project ORDER BY c.created_at;
-- Final independence additionally follows current incident/event grouping in JdbcVerifiedSkillSourceReader.
SELECT j.job_id,j.run_id,j.status,j.trigger_reason,j.attempts,j.last_error
FROM ai_ops_skill_evolution_job j WHERE j.project_id=@ops06_project AND j.session_id=@ops06_session ORDER BY j.id;
