-- Disable legacy Agent snapshots that cannot be parsed as JSON. The project
-- bootstrap recreates missing project defaults with the current serializer.

UPDATE ai_ops_agent_definition
SET enabled = 0,
    lifecycle = 'DISABLED'
WHERE JSON_VALID(definition_json) = 0;

UPDATE ai_ops_agent_definition_version
SET enabled = 0,
    current_published = 0,
    lifecycle = 'DISABLED'
WHERE JSON_VALID(definition_json) = 0;
