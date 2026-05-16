-- Deterministic Agent evaluation suites, repeatable cases and release-gate runs.

CREATE TABLE IF NOT EXISTS `ai_ops_agent_eval_suite` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `suite_id` VARCHAR(96) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL,
  `suite_name` VARCHAR(160) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `suite_version` INT NOT NULL DEFAULT 1,
  `created_by` VARCHAR(80) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_eval_suite_id` (`suite_id`),
  KEY `idx_agent_eval_suite_project` (`project_id`, `agent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent repeatable evaluation suites';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_eval_case` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `case_id` VARCHAR(128) NOT NULL,
  `suite_id` VARCHAR(96) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL,
  `case_name` VARCHAR(160) NOT NULL DEFAULT '',
  `case_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_eval_case_id` (`case_id`),
  KEY `idx_agent_eval_case_suite` (`suite_id`, `status`, `sort_order`),
  KEY `idx_agent_eval_case_project` (`project_id`, `agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent deterministic evaluation cases';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_eval_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `eval_run_id` VARCHAR(96) NOT NULL,
  `suite_id` VARCHAR(96) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL,
  `agent_version` INT NOT NULL,
  `definition_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `total_cases` INT NOT NULL,
  `passed_cases` INT NOT NULL DEFAULT 0,
  `failed_cases` INT NOT NULL DEFAULT 0,
  `result_json` MEDIUMTEXT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `created_by` VARCHAR(80) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_eval_run_id` (`eval_run_id`),
  KEY `idx_agent_eval_release_gate` (`project_id`, `agent_id`, `agent_version`, `definition_hash`, `status`),
  KEY `idx_agent_eval_run_suite` (`suite_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent evaluation release-gate runs';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_eval_case_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `case_run_id` VARCHAR(96) NOT NULL,
  `eval_run_id` VARCHAR(96) NOT NULL,
  `case_id` VARCHAR(128) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `agent_id` VARCHAR(128) NOT NULL,
  `agent_version` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `score` DECIMAL(8,5) NOT NULL DEFAULT 0,
  `reason_codes_json` TEXT NOT NULL,
  `actual_json` MEDIUMTEXT NOT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_eval_case_run_id` (`case_run_id`),
  UNIQUE KEY `uk_agent_eval_run_case` (`eval_run_id`, `case_id`),
  KEY `idx_agent_eval_case_run_project` (`project_id`, `agent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-case Agent evaluation results';
