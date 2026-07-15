-- Read-only inspection. Set this to the session printed by seed/runtime or shown in the browser URL.
SET @ops05_session = 'REPLACE_WITH_SESSION_ID';
SELECT session_id,project_id,user_id,title FROM ai_ops_chat_session WHERE session_id=@ops05_session;
SELECT message_seq,turn_id,role,content,capture_key FROM ai_ops_chat_message WHERE session_id=@ops05_session ORDER BY message_seq;
SELECT run_id,project_id,session_id,status,agent_id,agent_version FROM ai_ops_agent_run WHERE session_id=@ops05_session ORDER BY id;
SELECT s.* FROM ai_ops_task_episode_session s WHERE session_id=@ops05_session;
SELECT episode_id,goal,revision,outcome,verified_outcome_ref,last_consolidated_revision,last_activity_ms
FROM ai_ops_task_episode WHERE session_id=@ops05_session ORDER BY create_time;
SELECT turn_seq,end_seq,source_run_ref,episode_id,episode_revision,classifier_revision,status,attempts,epoch,
       input_hash,context_fidelity,last_error,next_attempt_ms
FROM ai_ops_task_episode_turn WHERE session_id=@ops05_session ORDER BY turn_seq;
SELECT run_id,snapshot_hash,create_time FROM ai_ops_task_episode_context WHERE session_id=@ops05_session ORDER BY create_time;
SELECT j.id,j.episode_id,j.revision,j.artifact_type,j.generator_hash,j.trigger_reasons,j.status,j.attempts,j.epoch,j.last_error
FROM ai_ops_episode_consolidation_job j JOIN ai_ops_task_episode e ON e.episode_id=j.episode_id WHERE e.session_id=@ops05_session ORDER BY j.id;
SELECT a.episode_id,a.revision,a.artifact_type,a.outcome,a.content_hash,a.input_hash,a.content
FROM ai_ops_episode_artifact a JOIN ai_ops_task_episode e ON e.episode_id=a.episode_id WHERE e.session_id=@ops05_session ORDER BY a.create_time;
SELECT t.result_id,t.run_id,t.tool_name,t.status,t.source,t.output_hash
FROM ai_ops_tool_result t JOIN ai_ops_agent_run r ON r.run_id=t.run_id WHERE r.session_id=@ops05_session ORDER BY t.id;
