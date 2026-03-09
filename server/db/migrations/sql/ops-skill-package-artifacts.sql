-- Store complete, append-only Skill Packages instead of limiting a Skill to SKILL.md.
-- UTF-8 text and allowlisted BASE64 binary assets are supported. A SCRIPT role describes a
-- reusable script source file; it does not grant execution permission and remains governed by Tool Policy.

CREATE TABLE IF NOT EXISTS `ai_ops_skill_artifact` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `artifact_id` VARCHAR(80) NOT NULL,
  `scope` VARCHAR(24) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL DEFAULT '',
  `skill_id` VARCHAR(128) NOT NULL,
  `version` INT NOT NULL,
  `package_hash` VARCHAR(128) NOT NULL,
  `artifact_path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `artifact_role` VARCHAR(32) NOT NULL DEFAULT 'RESOURCE',
  `media_type` VARCHAR(128) NOT NULL DEFAULT 'text/plain; charset=utf-8',
  `content_encoding` VARCHAR(16) NOT NULL DEFAULT 'UTF8',
  `content_hash` VARCHAR(128) NOT NULL,
  `size_bytes` BIGINT NOT NULL DEFAULT 0,
  `content` MEDIUMTEXT NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_artifact_version` (`scope`, `project_id`, `skill_id`, `version`, `artifact_path`),
  UNIQUE KEY `uk_skill_artifact_id` (`artifact_id`),
  KEY `idx_skill_artifact_package` (`package_hash`),
  KEY `idx_skill_artifact_lookup` (`project_id`, `skill_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Package append-only artifacts';

DROP PROCEDURE IF EXISTS ops_skill_artifact_add_column;
DELIMITER $$
CREATE PROCEDURE ops_skill_artifact_add_column(
  IN p_table_name VARCHAR(64),
  IN p_column_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table_name
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table_name AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_skill_artifact_add_column('ai_ops_skill_patch_candidate', 'artifacts_json',
  'MEDIUMTEXT NULL COMMENT ''Skill Authoring 生成的资源、脚本、模板和评测文件'' AFTER `changes_json`');
CALL ops_skill_artifact_add_column('ai_ops_skill_artifact', 'content_encoding',
  'VARCHAR(16) NOT NULL DEFAULT ''UTF8'' AFTER `media_type`');

DROP PROCEDURE IF EXISTS ops_skill_artifact_add_column;
