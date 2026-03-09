-- Cross-instance single-writer lease for repair workspaces.

DROP PROCEDURE IF EXISTS ops_repair_workspace_add_column;
DELIMITER $$
CREATE PROCEDURE ops_repair_workspace_add_column(IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='ai_ops_repair_workspace'
      AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE ai_ops_repair_workspace ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_repair_workspace_add_column('writer_lease_owner', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `created_by`');
CALL ops_repair_workspace_add_column('writer_lease_token', 'VARCHAR(80) NOT NULL DEFAULT '''' AFTER `writer_lease_owner`');
CALL ops_repair_workspace_add_column('writer_fencing_token', 'BIGINT NOT NULL DEFAULT 0 AFTER `writer_lease_token`');
CALL ops_repair_workspace_add_column('writer_lease_expires_at', 'DATETIME(3) NULL AFTER `writer_fencing_token`');
CALL ops_repair_workspace_add_column('state_version', 'BIGINT NOT NULL DEFAULT 1 AFTER `writer_lease_expires_at`');

DROP PROCEDURE IF EXISTS ops_repair_workspace_add_column;
