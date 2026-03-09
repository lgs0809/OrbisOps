-- Wrap every new Markdown skill version as an immutable v1 Skill Package.
-- Existing rows remain readable through the application compatibility wrapper;
-- subsequent writes persist the canonical package and artifact hashes.

DROP PROCEDURE IF EXISTS ops_skill_package_add_column;
DELIMITER $$
CREATE PROCEDURE ops_skill_package_add_column(
  IN p_table_name VARCHAR(64),
  IN p_column_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME=p_table_name
      AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_skill_package_add_column('ai_ops_skill', 'current_package_hash',
  'VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''当前Skill Package Hash'' AFTER `version_seq`');
CALL ops_skill_package_add_column('ai_ops_skill', 'package_manifest_json',
  'MEDIUMTEXT NULL COMMENT ''当前Skill Package Manifest'' AFTER `current_package_hash`');
CALL ops_skill_package_add_column('ai_ops_skill', 'artifact_hashes_json',
  'MEDIUMTEXT NULL COMMENT ''当前Artifact Hash集合'' AFTER `package_manifest_json`');

CALL ops_skill_package_add_column('ai_ops_skill_version', 'package_hash',
  'VARCHAR(128) NOT NULL DEFAULT '''' AFTER `publish_mode`');
CALL ops_skill_package_add_column('ai_ops_skill_version', 'manifest_json',
  'MEDIUMTEXT NULL AFTER `package_hash`');
CALL ops_skill_package_add_column('ai_ops_skill_version', 'artifact_hashes_json',
  'MEDIUMTEXT NULL AFTER `manifest_json`');
CALL ops_skill_package_add_column('ai_ops_skill_version', 'entrypoint',
  'VARCHAR(255) NOT NULL DEFAULT ''SKILL.md'' AFTER `artifact_hashes_json`');
CALL ops_skill_package_add_column('ai_ops_skill_version', 'artifact_count',
  'INT NOT NULL DEFAULT 1 AFTER `entrypoint`');
CALL ops_skill_package_add_column('ai_ops_skill_version', 'package_size',
  'BIGINT NOT NULL DEFAULT 0 AFTER `artifact_count`');

DROP PROCEDURE IF EXISTS ops_skill_package_add_column;
