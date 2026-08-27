-- PostgreSQL read-only inspection; no synthetic model vectors are installed in the acceptance DB.
SELECT scope,project_id,skill_id,skill_version,status,dimension,model_identity,created_at,ready_at
FROM ops_skill_route_generation ORDER BY created_at,generation_id;
SELECT g.status,COUNT(*) AS generations,COUNT(d.generation_id) AS complete_documents
FROM ops_skill_route_generation g LEFT JOIN ops_skill_route_document d USING(generation_id) GROUP BY g.status;
SELECT extname,extversion FROM pg_extension WHERE extname='vector';
