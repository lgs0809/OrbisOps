-- Read-only inspection. Empty evolution tables do not prove model authoring passed.
SELECT version,description,checksum FROM orbisops_schema_history WHERE version >= '080' ORDER BY version;
SELECT 'providers' AS fact,COUNT(*) AS value FROM ai_client_api
UNION ALL SELECT 'models',COUNT(*) FROM ai_client_model
UNION ALL SELECT 'accepted_tasks',COUNT(*) FROM ai_ops_task_acceptance
UNION ALL SELECT 'proposals',COUNT(*) FROM ai_ops_skill_evolution_proposal;
SELECT scope,project_id,skill_id,skill_name,origin,current_version,current_skill_hash,current_package_hash,
       version_seq,lifecycle_status,mutation_mode,execution_mode,auto_update_enabled,
       CHAR_LENGTH(content) AS body_characters,SHA2(content,256) AS body_sha256
FROM ai_ops_skill
WHERE project_id IN ('ops-acceptance-a','ops-acceptance-b') OR skill_name LIKE 'ops06-related-%'
ORDER BY scope,project_id,skill_id;
SELECT scope,project_id,skill_id,version,artifact_path,content_encoding,content_hash,size_bytes
FROM ai_ops_skill_artifact
WHERE project_id IN ('ops-acceptance-a','ops-acceptance-b') OR skill_id IN
 (SELECT skill_id FROM ai_ops_skill WHERE skill_name LIKE 'ops06-related-%')
ORDER BY scope,project_id,skill_id,version,artifact_path;
SELECT plan_id,project_id,source_id,plan_hash,
       JSON_UNQUOTE(JSON_EXTRACT(input_json,'$.relatedSkillPolicyVersion')) AS related_policy,
       JSON_LENGTH(JSON_EXTRACT(input_json,'$.relatedSkills')) AS related_count,
       JSON_LENGTH(JSON_EXTRACT(input_json,'$.consolidatedExperiences')) AS success_sources,
       authored_hash,candidate_id
FROM ai_ops_skill_evolution_proposal WHERE project_id IN ('ops-acceptance-a','ops-acceptance-b') ORDER BY create_time;
SELECT p.plan_id,r.skill_id,r.scope,r.project_id,r.source_type,r.version AS frozen_version,
       r.skill_hash AS frozen_hash,r.package_hash AS frozen_package_hash,
       c.current_version,c.current_skill_hash,c.current_package_hash,
       (c.current_version=r.version AND c.current_skill_hash=r.skill_hash AND c.current_package_hash=r.package_hash) AS db_identity_still_matches
FROM ai_ops_skill_evolution_proposal p
JOIN JSON_TABLE(p.input_json,'$.relatedSkills[*]' COLUMNS (
 skill_id VARCHAR(128) PATH '$.skillId',scope VARCHAR(24) PATH '$.scope',project_id VARCHAR(128) PATH '$.projectId',
 source_type VARCHAR(16) PATH '$.sourceType',version INT PATH '$.currentVersion',
 skill_hash VARCHAR(128) PATH '$.currentSkillHash',package_hash VARCHAR(128) PATH '$.currentPackageHash')) r
LEFT JOIN ai_ops_skill c ON c.scope=r.scope AND c.project_id=r.project_id AND c.skill_id=r.skill_id
WHERE p.project_id IN ('ops-acceptance-a','ops-acceptance-b') ORDER BY p.plan_id,r.scope,r.skill_id;

SELECT scope,project_id,skill_id,version,source_type,package_hash,SHA2(content,256) AS body_sha256 FROM ai_ops_skill_version WHERE skill_id LIKE 'skill-ops06-related-%' ORDER BY skill_id,version;
SELECT project_id,module_name,action_name,target_id,result_status,operator_name,create_time FROM ai_ops_config_audit WHERE target_id LIKE 'skill-ops06-related-%' ORDER BY id;
