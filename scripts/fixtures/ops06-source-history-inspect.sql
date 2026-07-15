-- Read-only inspection of published provenance. Manual fixtures are not learned history.
SELECT s.scope,s.project_id,s.skill_id,s.origin,s.current_version,v.version,v.source_type,v.evolution_job_id,
       p.plan_id,p.plan_hash,p.candidate_id,JSON_LENGTH(JSON_EXTRACT(p.input_json,'$.consolidatedExperiences')) AS source_count
FROM ai_ops_skill s LEFT JOIN ai_ops_skill_version v ON v.scope=s.scope AND v.project_id=s.project_id AND v.skill_id=s.skill_id
LEFT JOIN ai_ops_skill_evolution_proposal p ON p.project_id=v.project_id AND (p.candidate_id=v.evolution_job_id OR p.job_id=v.evolution_job_id)
WHERE s.project_id IN ('ops-acceptance-a','ops-acceptance-b') OR s.skill_id LIKE 'skill-ops06-related-%'
ORDER BY s.scope,s.project_id,s.skill_id,v.version;
SELECT p.plan_id,p.project_id,p.candidate_id,r.episode_id,r.source_id,r.condition_key,
       c.status AS candidate_status,c.reason_code
FROM ai_ops_skill_evolution_proposal p
JOIN JSON_TABLE(p.input_json,'$.consolidatedExperiences[*]' COLUMNS (
 episode_id VARCHAR(128) PATH '$.taskEpisodeId',source_id VARCHAR(128) PATH '$.sourceId',condition_key VARCHAR(512) PATH '$.conditionKey')) r
LEFT JOIN ai_ops_skill_patch_candidate c ON c.candidate_id=p.candidate_id
WHERE p.project_id IN ('ops-acceptance-a','ops-acceptance-b') ORDER BY p.plan_id,r.episode_id;
SELECT j.job_id,j.status,j.attempts,j.last_error,p.decision,p.skipped_reason
FROM ai_ops_skill_evolution_job j LEFT JOIN ai_ops_skill_evolution_patch p ON p.job_id=j.job_id
WHERE j.project_id IN ('ops-acceptance-a','ops-acceptance-b') ORDER BY j.id;
