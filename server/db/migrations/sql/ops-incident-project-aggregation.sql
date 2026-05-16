-- Add project isolation and repeat-alert aggregation to alert events and incidents.

DROP PROCEDURE IF EXISTS ops_incident_add_column;
DELIMITER $$
CREATE PROCEDURE ops_incident_add_column(
  IN p_table VARCHAR(64),
  IN p_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_incident_add_column(
  'ai_ops_alert_trigger_event',
  'project_id',
  'VARCHAR(80) NOT NULL DEFAULT '''' COMMENT ''Project isolation boundary'' AFTER `rule_name`'
);
CALL ops_incident_add_column(
  'ai_ops_incident',
  'project_id',
  'VARCHAR(80) NOT NULL DEFAULT '''' COMMENT ''Project isolation boundary'' AFTER `incident_id`'
);
CALL ops_incident_add_column(
  'ai_ops_incident',
  'occurrence_count',
  'BIGINT NOT NULL DEFAULT 1 COMMENT ''Aggregated occurrence count'' AFTER `metadata_json`'
);
CALL ops_incident_add_column(
  'ai_ops_incident',
  'affected_resources_json',
  'TEXT NULL COMMENT ''Affected resources'' AFTER `occurrence_count`'
);
CALL ops_incident_add_column(
  'ai_ops_incident',
  'first_seen_at',
  'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''First observed time'' AFTER `affected_resources_json`'
);
CALL ops_incident_add_column(
  'ai_ops_incident',
  'last_seen_at',
  'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''Most recent observed time'' AFTER `first_seen_at`'
);

DROP PROCEDURE IF EXISTS ops_incident_add_column;

UPDATE ai_ops_alert_trigger_event event_row
JOIN ai_ops_alert_trigger_rule trigger_rule ON trigger_rule.id = event_row.rule_id
SET event_row.project_id = trigger_rule.project_id
WHERE (event_row.project_id IS NULL OR event_row.project_id = '')
  AND trigger_rule.project_id IS NOT NULL
  AND trigger_rule.project_id <> '';

UPDATE ai_ops_incident
SET project_id = COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.projectId')), ''), project_id)
WHERE (project_id IS NULL OR project_id = '')
  AND metadata_json IS NOT NULL
  AND JSON_VALID(metadata_json) = 1;

UPDATE ai_ops_incident
SET first_seen_at = COALESCE(first_seen_at, create_time),
    last_seen_at = COALESCE(last_seen_at, update_time, create_time),
    occurrence_count = GREATEST(COALESCE(occurrence_count, 1), 1);

DROP PROCEDURE IF EXISTS ops_incident_add_index;
DELIMITER $$
CREATE PROCEDURE ops_incident_add_index(
  IN p_table VARCHAR(64),
  IN p_index VARCHAR(64),
  IN p_columns TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_incident_add_index(
  'ai_ops_alert_trigger_event',
  'idx_alert_event_project_time',
  '`project_id`, `create_time`'
);
CALL ops_incident_add_index(
  'ai_ops_incident',
  'idx_project_status_update',
  '`project_id`, `status`, `update_time`'
);

DROP PROCEDURE IF EXISTS ops_incident_add_index;
