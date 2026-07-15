-- Read-only MySQL facts. A completed workflow or synthetic test is not accepted business success.
SELECT scope,project_id,skill_id,status,current_version,current_skill_hash,current_package_hash,origin,update_mode
FROM ai_ops_skill WHERE project_id IN ('ops-acceptance-a','ops-acceptance-b') OR skill_id LIKE 'skill-ops06-related-%'
ORDER BY project_id,skill_id;
SELECT run_id,project_id,agent_id,used_skill_version_refs_json,used_skill_refs_hash,bundle_hash,create_time
FROM ai_ops_runtime_context_bundle WHERE project_id IN ('ops-acceptance-a','ops-acceptance-b')
ORDER BY id DESC LIMIT 20;
SELECT (SELECT COUNT(*) FROM ai_client_api) AS providers,(SELECT COUNT(*) FROM ai_client_model) AS models,
 (SELECT COUNT(*) FROM ai_ops_task_episode WHERE outcome='SUCCEEDED' AND verified_outcome_ref<>'') AS accepted_tasks,
 (SELECT COUNT(*) FROM ai_ops_skill_evolution_proposal) AS actual_proposals;
