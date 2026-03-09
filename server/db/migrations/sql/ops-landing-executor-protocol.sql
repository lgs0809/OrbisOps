-- Durable executor protocol metadata for fencing, lease recovery and outcome reconciliation.

DROP PROCEDURE IF EXISTS ops_landing_protocol_add_column;
DELIMITER $$
CREATE PROCEDURE ops_landing_protocol_add_column(IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='ai_ops_change_package_landing_operation_run'
      AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_change_package_landing_operation_run` ADD COLUMN `',
                      p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_landing_protocol_add_column('state_version', 'BIGINT NOT NULL DEFAULT 0 AFTER `dispatch_attempts`');
CALL ops_landing_protocol_add_column('fencing_token', 'BIGINT NOT NULL DEFAULT 0 AFTER `state_version`');
CALL ops_landing_protocol_add_column('worker_id', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `fencing_token`');
CALL ops_landing_protocol_add_column('lease_expires_at', 'DATETIME(3) NULL AFTER `worker_id`');
CALL ops_landing_protocol_add_column('remote_request_id', 'VARCHAR(160) NOT NULL DEFAULT '''' AFTER `lease_expires_at`');
CALL ops_landing_protocol_add_column('remote_result_id', 'VARCHAR(160) NOT NULL DEFAULT '''' AFTER `remote_request_id`');
CALL ops_landing_protocol_add_column('request_hash', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `remote_result_id`');
CALL ops_landing_protocol_add_column('dispatched_at', 'DATETIME(3) NULL AFTER `request_hash`');
CALL ops_landing_protocol_add_column('acknowledged_at', 'DATETIME(3) NULL AFTER `dispatched_at`');
CALL ops_landing_protocol_add_column('verified_at', 'DATETIME(3) NULL AFTER `acknowledged_at`');
CALL ops_landing_protocol_add_column('unknown_reason_code', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `verified_at`');

DROP PROCEDURE IF EXISTS ops_landing_protocol_add_index;
DELIMITER $$
CREATE PROCEDURE ops_landing_protocol_add_index()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='ai_ops_change_package_landing_operation_run'
      AND INDEX_NAME='idx_operation_reconcile_lease'
  ) THEN
    ALTER TABLE `ai_ops_change_package_landing_operation_run`
      ADD KEY `idx_operation_reconcile_lease` (`fact_status`, `status`, `lease_expires_at`);
  END IF;
END$$
DELIMITER ;

CALL ops_landing_protocol_add_index();

DROP PROCEDURE IF EXISTS ops_landing_protocol_add_column;
DROP PROCEDURE IF EXISTS ops_landing_protocol_add_index;
