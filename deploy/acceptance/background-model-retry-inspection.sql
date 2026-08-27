-- Read-only, no payloads or model credentials. Run against the isolated acceptance database.
SELECT 'EPISODE_TURN' AS family,status,COUNT(*) AS tasks,MIN(attempts) AS min_attempts,MAX(attempts) AS max_attempts
FROM ai_ops_task_episode_turn GROUP BY status;
SELECT 'EPISODE_CONSOLIDATION' AS family,status,COUNT(*) AS tasks,MIN(attempts) AS min_attempts,MAX(attempts) AS max_attempts
FROM ai_ops_episode_consolidation_job GROUP BY status;
SELECT 'MEMORY' AS family,task_type,status,result_kind,COUNT(*) AS tasks,MAX(attempts) AS max_attempts
FROM ai_ops_memory_post_processing GROUP BY task_type,status,result_kind;
SELECT 'SKILL_REVIEW' AS family,status,COUNT(*) AS tasks,MAX(attempt_count) AS max_attempts
FROM ai_ops_skill_canary_review GROUP BY status;
SELECT 'SKILL_EVOLUTION' AS family,status,last_error,COUNT(*) AS tasks,MAX(attempts) AS max_attempts
FROM ai_ops_skill_evolution_job GROUP BY status,last_error;
SELECT recovery_id,family,task_id,project_id,actor,prior_status,prior_attempts,prior_error,
  next_attempt_ms,FROM_UNIXTIME(next_attempt_ms/1000) AS next_attempt_at,created_at
FROM ai_ops_background_model_recovery_audit ORDER BY created_at DESC LIMIT 50;
SELECT t.id,t.project_id,t.status,t.attempts,t.last_error,t.input_hash,t.next_attempt_ms,t.lease_until_ms
FROM ai_ops_task_episode_turn t WHERE t.status IN ('WAITING_MODEL','PENDING_RETRY','RETRY_EXHAUSTED') ORDER BY t.id LIMIT 50;
SELECT review_id,project_id,release_id,episode_id,status,attempt_count,reason_code,next_attempt_at,lease_until
FROM ai_ops_skill_canary_review WHERE status<>'COMPLETED' ORDER BY created_at DESC LIMIT 50;

-- Artifact/database migration identity (no credentials).
SELECT version,description,checksum,applied_at FROM orbisops_schema_history WHERE version IN ('104','105');
-- Original acceptance memory rows: keep the exact stable ordering for before/after hashes.
SELECT id,task_type,status,attempts,result_kind,last_error,next_attempt_ms,epoch
FROM ai_ops_memory_post_processing WHERE id<=6690 ORDER BY id;
