-- Read-only inspection after migration 090. Do not infer business success from a Run or review status.
USE orbisops_acceptance;
START TRANSACTION READ ONLY;
SELECT release_id,project_id,target_skill_id,status,reason_code,
       JSON_CONTAINS_PATH(IF(JSON_VALID(metadata_json),metadata_json,'{}'),'one','$.observationContract.contractHash') AS has_frozen_contract
FROM ai_ops_skill_release ORDER BY id DESC LIMIT 100;
SELECT project_id,release_id,episode_id,status,attempt_count,next_attempt_at,lease_until,
       safety,attribution,reviewer_model,reviewer_version,reason_code,
       input_hash=SHA2(input_json,256) AS input_integrity,
       CASE WHEN result_json IS NULL THEN NULL ELSE result_hash=SHA2(result_json,256) END AS result_integrity
FROM ai_ops_skill_canary_review ORDER BY created_at DESC LIMIT 100;
SELECT b.project_id,b.run_id,refs.release_id,t.episode_id,e.revision,e.outcome,
       e.verified_outcome_ref,b.used_skill_refs_hash
FROM ai_ops_runtime_context_bundle b
JOIN JSON_TABLE(IF(JSON_VALID(b.used_skill_version_refs_json),b.used_skill_version_refs_json,'[]'),
     '$[*]' COLUMNS(release_id VARCHAR(80) PATH '$.releaseId')) refs
LEFT JOIN ai_ops_task_episode_turn t ON t.project_id=b.project_id AND t.source_run_ref=b.run_id AND t.status='ASSIGNED'
LEFT JOIN ai_ops_task_episode e ON e.project_id=b.project_id AND e.episode_id=t.episode_id
WHERE refs.release_id IS NOT NULL ORDER BY b.id DESC LIMIT 100;
COMMIT;
