-- Repair stale UI version rows that shadow classpath preset agents.
-- These rows were produced by older validation flows and are not runnable.

UPDATE ai_ops_agent_definition_version
SET enabled = 0,
    current_published = 0,
    lifecycle = 'DISABLED'
WHERE agent_id IN ('generic-ops-react-agent', 'demo-ops-agent')
  AND source = 'UI'
  AND lifecycle = 'VALIDATED'
  AND current_published = 0;

UPDATE ai_agent_task_schedule
SET project_id = 'demo-project',
    task_param = CASE
        WHEN JSON_VALID(task_param) THEN JSON_SET(task_param, '$.projectId', 'demo-project')
        ELSE task_param
    END
WHERE agent_id = 'demo-ops-agent'
  AND (project_id IS NULL OR project_id = '');
