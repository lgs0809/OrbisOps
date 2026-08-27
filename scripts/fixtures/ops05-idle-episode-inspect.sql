-- Read only. 24-hour eligibility is measured from the latest conversation activity.
-- A PROGRESS artifact is not proof of business success.
START TRANSACTION READ ONLY;
SELECT e.episode_id,e.project_id,e.session_id,e.revision,e.last_consolidated_revision,e.outcome,
 FROM_UNIXTIME(e.last_activity_ms/1000) AS episode_last_activity,
 (SELECT MAX(m.create_time) FROM ai_ops_chat_message m WHERE m.session_id=e.session_id) AS latest_message,
 e.last_activity_ms <= UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000-86400000
 AND NOT EXISTS(SELECT 1 FROM ai_ops_chat_message m WHERE m.session_id=e.session_id
                AND m.create_time>CURRENT_TIMESTAMP(3)-INTERVAL 24 HOUR) AS idle_24h
FROM ai_ops_task_episode e WHERE e.project_id='ops-acceptance-a' ORDER BY e.last_activity_ms DESC LIMIT 30;
SELECT j.episode_id,j.revision,j.artifact_type,j.trigger_reasons,j.status,j.attempts,j.last_error
FROM ai_ops_episode_consolidation_job j JOIN ai_ops_task_episode e ON e.episode_id=j.episode_id
WHERE e.project_id='ops-acceptance-a' ORDER BY j.id DESC LIMIT 30;
COMMIT;
