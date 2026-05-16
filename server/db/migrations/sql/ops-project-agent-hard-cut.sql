-- The generic ReAct definition is an internal cloning blueprint, not a project Agent.

ALTER TABLE ai_ops_project
    ALTER COLUMN default_agent_id SET DEFAULT '';

UPDATE ai_ops_project
SET default_agent_id = ''
WHERE default_agent_id = 'generic-ops-react-agent';
