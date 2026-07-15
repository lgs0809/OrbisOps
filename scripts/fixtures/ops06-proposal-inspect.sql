-- Read-only inspection. No credentials, claim tokens, model inputs or successful fixture outcomes are imported.
SELECT version,description,checksum FROM orbisops_schema_history WHERE version IN ('081','082','083','084') ORDER BY version;
SELECT (SELECT COUNT(*) FROM ai_ops_skill_evolution_proposal) AS actual_proposals,
       (SELECT COUNT(*) FROM ai_ops_skill_pending_asset_proposal) AS actual_asset_slots,
       (SELECT COUNT(*) FROM ai_ops_task_acceptance) AS actual_acceptance_records;
SELECT p.plan_id,p.job_id,p.source_id,p.project_id,p.agent_id,p.cluster_key,p.plan_hash,
       SHA2(p.input_json,256) AS computed_plan_hash,
       JSON_LENGTH(p.input_json,'$.consolidatedExperiences') AS full_source_count,
       CHAR_LENGTH(p.input_json) AS author_input_chars,
       IF(p.authored_json IS NULL,'NOT_RECORDED','RECORDED') AS model_result_state,
       p.authored_hash,IF(p.authored_json IS NULL,NULL,SHA2(p.authored_json,256)) AS computed_authored_hash,
       p.candidate_id,p.asset_set_hash,j.status AS job_status,j.attempts,j.last_error,j.next_run_at
FROM ai_ops_skill_evolution_proposal p JOIN ai_ops_skill_evolution_job j ON j.job_id=p.job_id
ORDER BY p.create_time DESC LIMIT 30;
SELECT g.project_id,g.asset_set_hash,g.assets_json,g.plan_id,g.candidate_id,g.next_proposal_at_ms,c.status AS candidate_status
FROM ai_ops_skill_pending_asset_proposal g LEFT JOIN ai_ops_skill_patch_candidate c ON c.candidate_id=g.candidate_id
ORDER BY g.update_time DESC LIMIT 30;
SELECT p.plan_id,s.sourceId,s.runId,s.taskEpisodeId,s.conditionKey,s.sourceHash
FROM ai_ops_skill_evolution_proposal p,
 JSON_TABLE(p.input_json,'$.consolidatedExperiences[*]' COLUMNS(
  sourceId VARCHAR(80) PATH '$.sourceId',runId VARCHAR(80) PATH '$.runId',
  taskEpisodeId VARCHAR(80) PATH '$.taskEpisodeId',conditionKey VARCHAR(128) PATH '$.conditionKey',
  sourceHash VARCHAR(64) PATH '$.sourceHash')) s
WHERE p.project_id='ops-acceptance-a' ORDER BY p.create_time DESC,s.sourceId LIMIT 100;
