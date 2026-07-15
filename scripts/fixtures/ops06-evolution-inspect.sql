-- Read only. Select a real run created through Chat/Workbench; this never manufactures task acceptance.
SET @run_id = 'REPLACE_WITH_RUN_ID';
SELECT run_id,session_id,project_id,agent_id,status,updated_at FROM ai_ops_agent_run WHERE run_id=@run_id;
SELECT j.job_id,j.run_id,j.status,j.attempts,j.trigger_reason,j.last_error,s.source_id,s.epoch,s.lease_until_ms,
       IF(s.lease_token='','RELEASED','HELD') AS lease_status
FROM ai_ops_skill_evolution_job j LEFT JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id WHERE j.run_id=@run_id;
SELECT patch_id,job_id,decision,status,skipped_reason,create_time,SHA2(patch_json,256) AS patch_hash
FROM ai_ops_skill_evolution_patch WHERE run_id=@run_id ORDER BY id;
SELECT source_id,episode_id,episode_revision,input_hash,SHA2(input_json,256) AS computed_hash,
       CHAR_LENGTH(input_json) AS source_chars,candidate_id
FROM ai_ops_skill_evolution_source WHERE run_id=@run_id;
SELECT t.source_run_ref,t.episode_id,t.episode_revision,t.status,e.outcome,e.verified_outcome_ref
FROM ai_ops_task_episode_turn t LEFT JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id
WHERE t.session_id=(SELECT session_id FROM ai_ops_agent_run WHERE run_id=@run_id) ORDER BY t.turn_seq;
SELECT result_id,source,tool_name,status,output_hash,SHA2(full_output,256) AS computed_hash
FROM ai_ops_tool_result WHERE run_id=@run_id ORDER BY id;
SELECT version,description,checksum FROM orbisops_schema_history WHERE version IN ('081','082','083');
