-- Converge Source Repository persistence with the LOCAL / MCP access-mode domain model.
-- Production keeps source-repository auto-init disabled, so these columns must be migration-owned.

DROP PROCEDURE IF EXISTS ops_add_source_repository_column;
DELIMITER $$
CREATE PROCEDURE ops_add_source_repository_column(
    IN p_column_name VARCHAR(128),
    IN p_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ai_ops_source_repository'
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `ai_ops_source_repository` ADD COLUMN `',
            p_column_name,
            '` ',
            p_definition
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL ops_add_source_repository_column(
    'access_mode',
    'VARCHAR(16) NOT NULL DEFAULT ''LOCAL'' AFTER `local_path`'
);
CALL ops_add_source_repository_column(
    'code_mcp_id',
    'VARCHAR(100) NOT NULL DEFAULT '''' AFTER `access_mode`'
);
CALL ops_add_source_repository_column(
    'logical_root',
    'VARCHAR(100) NOT NULL DEFAULT '''' AFTER `code_mcp_id`'
);

DROP PROCEDURE IF EXISTS ops_add_source_repository_column;
