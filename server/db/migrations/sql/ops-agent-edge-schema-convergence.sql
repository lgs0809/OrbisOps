-- Converge production Agent edge persistence with the normalized graph repository.
-- Test/dev schema auto-init already owns these columns; prod must receive them through migrations.

DROP PROCEDURE IF EXISTS ops_add_agent_edge_column;
DELIMITER $$
CREATE PROCEDURE ops_add_agent_edge_column(
    IN p_column_name VARCHAR(128),
    IN p_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ai_ops_agent_edge'
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `ai_ops_agent_edge` ADD COLUMN `',
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

CALL ops_add_agent_edge_column(
    'edge_id',
    'VARCHAR(128) NULL COMMENT ''连线ID'' AFTER `agent_id`'
);
CALL ops_add_agent_edge_column(
    'edge_name',
    'VARCHAR(128) NULL COMMENT ''连线名称'' AFTER `edge_id`'
);
CALL ops_add_agent_edge_column(
    'condition_type',
    'VARCHAR(48) NOT NULL DEFAULT ''always'' COMMENT ''条件类型'' AFTER `to_node_id`'
);
CALL ops_add_agent_edge_column('default_edge', 'TINYINT NULL COMMENT ''是否默认边'' AFTER `condition_expr`');
CALL ops_add_agent_edge_column('feedback_edge', 'TINYINT NULL COMMENT ''是否回边'' AFTER `default_edge`');
CALL ops_add_agent_edge_column('priority_order', 'INT NULL COMMENT ''条件优先级'' AFTER `feedback_edge`');
CALL ops_add_agent_edge_column(
    'data_mapping_json',
    'TEXT NULL COMMENT ''数据映射JSON'' AFTER `priority_order`'
);

DROP PROCEDURE IF EXISTS ops_add_agent_edge_column;
