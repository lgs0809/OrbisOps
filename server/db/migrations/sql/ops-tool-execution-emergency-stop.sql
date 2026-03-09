CREATE TABLE IF NOT EXISTS `ai_ops_tool_execution_emergency_stop` (
  `project_id` VARCHAR(128) NOT NULL,
  `active` TINYINT NOT NULL DEFAULT 0,
  `reason` VARCHAR(500) NOT NULL DEFAULT '',
  `actor` VARCHAR(128) NOT NULL DEFAULT '',
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`project_id`),
  KEY `idx_tool_emergency_stop_active` (`active`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ToolExecution project emergency stop facts';
