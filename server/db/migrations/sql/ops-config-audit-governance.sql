-- 运维审计治理增量迁移。兼容既有 ai_ops_config_audit，不删除旧字段或数据。
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

CALL add_column_if_missing('ai_ops_config_audit', 'audit_id', '`audit_id` VARCHAR(64) NULL COMMENT ''审计业务ID'' AFTER `id`');
CALL add_column_if_missing('ai_ops_config_audit', 'agent_id', '`agent_id` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''关联Agent'' AFTER `project_id`');
CALL add_column_if_missing('ai_ops_config_audit', 'target_type', '`target_type` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''目标类型'' AFTER `action_name`');
CALL add_column_if_missing('ai_ops_config_audit', 'risk_level', '`risk_level` VARCHAR(16) NOT NULL DEFAULT ''LOW'' COMMENT ''风险等级'' AFTER `target_id`');
CALL add_column_if_missing('ai_ops_config_audit', 'result_status', '`result_status` VARCHAR(16) NOT NULL DEFAULT ''SUCCESS'' COMMENT ''执行结果'' AFTER `risk_level`');
CALL add_column_if_missing('ai_ops_config_audit', 'trace_id', '`trace_id` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''链路追踪ID'' AFTER `client_ip`');
DROP PROCEDURE IF EXISTS add_column_if_missing;

SET @audit_id_index_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_config_audit'
    AND INDEX_NAME = 'idx_audit_id'
);
SET @audit_id_index_sql := IF(
  @audit_id_index_exists = 0,
  'CREATE INDEX `idx_audit_id` ON `ai_ops_config_audit` (`audit_id`)',
  'SELECT 1'
);
PREPARE audit_id_index_stmt FROM @audit_id_index_sql;
EXECUTE audit_id_index_stmt;
DEALLOCATE PREPARE audit_id_index_stmt;

SET @audit_agent_index_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_config_audit'
    AND INDEX_NAME = 'idx_agent_time'
);
SET @audit_agent_index_sql := IF(
  @audit_agent_index_exists = 0,
  'CREATE INDEX `idx_agent_time` ON `ai_ops_config_audit` (`agent_id`, `create_time`)',
  'SELECT 1'
);
PREPARE audit_agent_index_stmt FROM @audit_agent_index_sql;
EXECUTE audit_agent_index_stmt;
DEALLOCATE PREPARE audit_agent_index_stmt;

SET @audit_operator_index_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_config_audit'
    AND INDEX_NAME = 'idx_operator_time'
);
SET @audit_operator_index_sql := IF(
  @audit_operator_index_exists = 0,
  'CREATE INDEX `idx_operator_time` ON `ai_ops_config_audit` (`operator_id`, `create_time`)',
  'SELECT 1'
);
PREPARE audit_operator_index_stmt FROM @audit_operator_index_sql;
EXECUTE audit_operator_index_stmt;
DEALLOCATE PREPARE audit_operator_index_stmt;

CREATE TABLE IF NOT EXISTS `ai_ops_audit_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `project_id` VARCHAR(128) NOT NULL COMMENT '项目ID，GLOBAL 表示平台默认',
  `retention_days` INT NOT NULL DEFAULT 180 COMMENT '审计保留天数',
  `masking_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用脱敏',
  `export_approval_required` TINYINT NOT NULL DEFAULT 1 COMMENT '导出是否需要审批',
  `high_risk_confirmation_required` TINYINT NOT NULL DEFAULT 1 COMMENT '高风险操作是否二次确认',
  `replay_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许审计回放',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维审计策略表';
