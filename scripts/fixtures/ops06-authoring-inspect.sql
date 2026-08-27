-- Read-only authoring readiness and provenance. Credentials and lease tokens are deliberately excluded.
SELECT api_id,provider_name,provider_type,status FROM ai_client_api ORDER BY api_id;
SELECT model_id,api_id,model_name,model_type,model_usage,status FROM ai_client_model ORDER BY model_id;
SELECT COUNT(*) AS accepted_task_records FROM ai_ops_task_acceptance;
SELECT j.job_id,j.project_id,j.run_id,j.status,j.attempts,j.last_error,j.next_run_at,
       s.source_id,s.epoch,s.lease_until_ms,IF(s.lease_token='','RELEASED','HELD') AS lease_state
FROM ai_ops_skill_evolution_job j LEFT JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
ORDER BY j.id DESC LIMIT 20;
SELECT source_id,project_id,run_id,episode_id,episode_revision,input_hash,
       SHA2(input_json,256) AS computed_input_hash,CHAR_LENGTH(input_json) AS complete_source_chars,candidate_id
FROM ai_ops_skill_evolution_source ORDER BY create_time DESC LIMIT 20;
SELECT candidate_id,project_id,source_run_id,patch_type,status,reason_code FROM ai_ops_skill_patch_candidate
ORDER BY id DESC LIMIT 20;
