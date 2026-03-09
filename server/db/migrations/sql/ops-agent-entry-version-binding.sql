-- Chat-adjacent entry points expose ReAct / named Workflow as the product execution choice,
-- then resolve an exact published Agent definition for every Run. Channel may also be NONE for output-only transport.
-- LATEST_PUBLISHED resolves when the Run is enqueued; PINNED_VERSION keeps the selected Workflow version.

DROP PROCEDURE IF EXISTS ops_add_agent_entry_column;
DELIMITER $$
CREATE PROCEDURE ops_add_agent_entry_column(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL ops_add_agent_entry_column(
    'ai_ops_channel',
    'execution_kind',
    'VARCHAR(16) NOT NULL DEFAULT ''WORKFLOW'' COMMENT ''NONE/REACT/WORKFLOW; existing Channel rows migrate as WORKFLOW'''
);
CALL ops_add_agent_entry_column(
    'ai_ops_channel',
    'agent_binding_mode',
    'VARCHAR(24) NOT NULL DEFAULT ''LATEST_PUBLISHED'' COMMENT ''Compatibility column for Workflow version policy'''
);
CALL ops_add_agent_entry_column(
    'ai_ops_channel',
    'agent_version',
    'INT NULL COMMENT ''Compatibility column for pinned or last resolved Workflow version'''
);
CALL ops_add_agent_entry_column(
    'ai_ops_channel',
    'agent_definition_hash',
    'VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''Compatibility column for resolved Workflow definition hash'''
);

CALL ops_add_agent_entry_column(
    'ai_ops_chat_session',
    'agent_binding_mode',
    'VARCHAR(24) NOT NULL DEFAULT ''LATEST_PUBLISHED'' COMMENT ''Agent version binding mode'''
);
CALL ops_add_agent_entry_column(
    'ai_ops_chat_session',
    'agent_definition_hash',
    'VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''Resolved Agent definition hash'''
);

CALL ops_add_agent_entry_column(
    'ai_ops_alert_trigger_rule',
    'agent_binding_mode',
    'VARCHAR(24) NOT NULL DEFAULT ''LATEST_PUBLISHED'' COMMENT ''Agent version binding mode'''
);
CALL ops_add_agent_entry_column(
    'ai_ops_alert_trigger_rule',
    'agent_version',
    'INT NULL COMMENT ''Pinned or last resolved Agent version'''
);
CALL ops_add_agent_entry_column(
    'ai_ops_alert_trigger_rule',
    'agent_definition_hash',
    'VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''Resolved Agent definition hash'''
);

UPDATE ai_ops_channel
SET agent_binding_mode = 'LATEST_PUBLISHED'
WHERE agent_binding_mode IS NULL OR agent_binding_mode = '';

UPDATE ai_ops_chat_session
SET agent_binding_mode = 'LATEST_PUBLISHED'
WHERE agent_binding_mode IS NULL OR agent_binding_mode = '';

UPDATE ai_ops_alert_trigger_rule
SET agent_binding_mode = 'LATEST_PUBLISHED'
WHERE agent_binding_mode IS NULL OR agent_binding_mode = '';

DROP PROCEDURE IF EXISTS ops_add_agent_entry_column;
