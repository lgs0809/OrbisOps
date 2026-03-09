CREATE TABLE IF NOT EXISTS `ai_ops_skill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `skill_id` VARCHAR(128) NOT NULL COMMENT 'Skill ID',
  `project_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
  `skill_name` VARCHAR(160) NOT NULL COMMENT 'Skill名称',
  `scope` VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
  `source_global_skill_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源通用Skill',
  `description` TEXT NULL COMMENT '说明',
  `content` MEDIUMTEXT NULL COMMENT 'Skill正文',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `create_by` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
  `current_version` INT NOT NULL DEFAULT 1 COMMENT '当前指针版本',
  `current_skill_hash` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '当前指针Hash',
  `version_seq` INT NOT NULL DEFAULT 1 COMMENT '版本序列',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_skill` (`scope`, `project_id`, `skill_id`),
  KEY `idx_project_status` (`project_id`, `status`),
  KEY `idx_scope_status` (`scope`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Skill目录表';

DROP PROCEDURE IF EXISTS add_ai_ops_skill_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_skill_column(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_skill'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_skill` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_ai_ops_skill_column('origin', '`origin` VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL/EVOLVED/IMPORTED''');
CALL add_ai_ops_skill_column('update_mode', '`update_mode` VARCHAR(32) NOT NULL DEFAULT ''AUTO'' COMMENT ''AUTO/MANUAL_ONLY/FROZEN''');
CALL add_ai_ops_skill_column('auto_update_enabled', '`auto_update_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许自动更新''');
CALL add_ai_ops_skill_column('auto_merge_enabled', '`auto_merge_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许相似合并''');
CALL add_ai_ops_skill_column('last_evolved_at', '`last_evolved_at` TIMESTAMP NULL DEFAULT NULL COMMENT ''最近自动进化时间''');
CALL add_ai_ops_skill_column('frozen_reason', '`frozen_reason` VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''冻结原因''');
CALL add_ai_ops_skill_column('frozen_by', '`frozen_by` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''冻结人''');
CALL add_ai_ops_skill_column('frozen_at', '`frozen_at` TIMESTAMP NULL DEFAULT NULL COMMENT ''冻结时间''');
CALL add_ai_ops_skill_column('skill_hash', '`skill_hash` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''Skill规范化Hash''');
CALL add_ai_ops_skill_column('current_version', '`current_version` INT NOT NULL DEFAULT 1 COMMENT ''当前指针版本''');
CALL add_ai_ops_skill_column('current_skill_hash', '`current_skill_hash` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''当前指针Hash''');
CALL add_ai_ops_skill_column('version_seq', '`version_seq` INT NOT NULL DEFAULT 1 COMMENT ''版本序列''');

DROP PROCEDURE IF EXISTS add_ai_ops_skill_column;

CREATE TABLE IF NOT EXISTS `ai_ops_skill_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `skill_id` VARCHAR(128) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `scope` VARCHAR(24) NOT NULL,
  `version` INT NOT NULL,
  `skill_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `base_version` INT NOT NULL DEFAULT 0,
  `base_skill_hash` VARCHAR(128) NOT NULL DEFAULT '',
  `source_run_id` VARCHAR(80) NOT NULL DEFAULT '',
  `source_session_id` VARCHAR(80) NOT NULL DEFAULT '',
  `evolution_job_id` VARCHAR(80) NOT NULL DEFAULT '',
  `publish_mode` VARCHAR(32) NOT NULL DEFAULT '',
  `content` MEDIUMTEXT NOT NULL,
  `source_type` VARCHAR(32) DEFAULT '',
  `source_trace_id` VARCHAR(80) DEFAULT '',
  `change_summary` TEXT,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version` (`scope`, `project_id`, `skill_id`, `version`),
  KEY `idx_skill_id` (`skill_id`),
  KEY `idx_source_trace` (`source_trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Skill版本表';

DROP PROCEDURE IF EXISTS add_ai_ops_skill_version_column;
DELIMITER $$
CREATE PROCEDURE add_ai_ops_skill_version_column(IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_ops_skill_version'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `ai_ops_skill_version` ADD COLUMN ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_ai_ops_skill_version_column('skill_hash', '`skill_hash` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `version`');
CALL add_ai_ops_skill_version_column('base_version', '`base_version` INT NOT NULL DEFAULT 0 AFTER `skill_hash`');
CALL add_ai_ops_skill_version_column('base_skill_hash', '`base_skill_hash` VARCHAR(128) NOT NULL DEFAULT '''' AFTER `base_version`');
CALL add_ai_ops_skill_version_column('source_run_id', '`source_run_id` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `base_skill_hash`');
CALL add_ai_ops_skill_version_column('source_session_id', '`source_session_id` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `source_run_id`');
CALL add_ai_ops_skill_version_column('evolution_job_id', '`evolution_job_id` VARCHAR(80) NOT NULL DEFAULT '''' AFTER `source_session_id`');
CALL add_ai_ops_skill_version_column('publish_mode', '`publish_mode` VARCHAR(32) NOT NULL DEFAULT '''' AFTER `evolution_job_id`');

DROP PROCEDURE IF EXISTS add_ai_ops_skill_version_column;
