-- Async trigger/task registry. Durable Agent execution remains exclusively in ai_ops_agent_run.

CREATE TABLE IF NOT EXISTS `ai_ops_analysis_task` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(80) NOT NULL COMMENT 'Canonical Work Session runId',
  `project_id` VARCHAR(80) NOT NULL COMMENT 'Project isolation boundary',
  `trigger_source` VARCHAR(48) NOT NULL DEFAULT '' COMMENT 'CHAT/ALERTMANAGER/SCHEDULE/CHANNEL/ADMIN',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED',
  `request_json` MEDIUMTEXT NULL,
  `response_json` MEDIUMTEXT NULL,
  `error_message` TEXT NULL,
  `created_at` VARCHAR(32) NOT NULL,
  `updated_at` VARCHAR(32) NOT NULL,
  `duration_ms` BIGINT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_task_run` (`run_id`),
  KEY `idx_analysis_project_status` (`project_id`, `status`),
  KEY `idx_analysis_trigger_status` (`trigger_source`, `status`),
  KEY `idx_analysis_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Async analysis task registry linked to durable Work Session';
