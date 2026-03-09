DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN p_table_name VARCHAR(128), IN p_column_name VARCHAR(128), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_column_if_missing('ai_ops_skill', 'origin', '`origin` VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL/EVOLVED/IMPORTED'' AFTER `status`');
CALL add_column_if_missing('ai_ops_skill', 'update_mode', '`update_mode` VARCHAR(32) NOT NULL DEFAULT ''AUTO'' COMMENT ''AUTO/MANUAL_ONLY/FROZEN'' AFTER `origin`');
CALL add_column_if_missing('ai_ops_skill', 'auto_update_enabled', '`auto_update_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许自动更新'' AFTER `update_mode`');
CALL add_column_if_missing('ai_ops_skill', 'auto_merge_enabled', '`auto_merge_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许相似合并'' AFTER `auto_update_enabled`');
CALL add_column_if_missing('ai_ops_skill', 'last_evolved_at', '`last_evolved_at` TIMESTAMP NULL DEFAULT NULL COMMENT ''最近自动进化时间'' AFTER `auto_merge_enabled`');
DROP PROCEDURE IF EXISTS add_column_if_missing;

CREATE TABLE IF NOT EXISTS `ai_ops_skill_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `skill_id` VARCHAR(128) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `scope` VARCHAR(24) NOT NULL,
  `version` INT NOT NULL,
  `content` MEDIUMTEXT NOT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT '',
  `source_trace_id` VARCHAR(80) NOT NULL DEFAULT '',
  `change_summary` TEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version` (`scope`, `project_id`, `skill_id`, `version`),
  KEY `idx_skill_id` (`skill_id`),
  KEY `idx_source_trace` (`source_trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Skill版本表';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_job` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_id` VARCHAR(80) NOT NULL,
  `run_id` VARCHAR(80) NOT NULL DEFAULT '',
  `session_id` VARCHAR(80) NOT NULL DEFAULT '',
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `agent_id` VARCHAR(128) NOT NULL DEFAULT '',
  `trigger_reason` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `attempts` INT NOT NULL DEFAULT 0,
  `next_run_at` TIMESTAMP NULL DEFAULT NULL,
  `last_error` TEXT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_status_next_run` (`status`, `next_run_at`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill自动进化任务表';

CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_patch` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `patch_id` VARCHAR(80) NOT NULL,
  `job_id` VARCHAR(80) NOT NULL,
  `run_id` VARCHAR(80) NOT NULL DEFAULT '',
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `target_skill_id` VARCHAR(128) NOT NULL DEFAULT '',
  `decision` VARCHAR(48) NOT NULL,
  `patch_json` MEDIUMTEXT NULL,
  `validation_json` MEDIUMTEXT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `applied_version` INT DEFAULT NULL,
  `skipped_reason` VARCHAR(128) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patch_id` (`patch_id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_target_skill` (`project_id`, `target_skill_id`),
  KEY `idx_decision` (`decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill自动进化Patch记录表';
