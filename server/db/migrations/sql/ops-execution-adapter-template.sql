-- 执行适配器模板与项目执行目标。增量执行，不影响已有 ai_ops_execution_resource 数据。

CREATE TABLE IF NOT EXISTS `ai_ops_execution_adapter_template` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `adapter_template_id` VARCHAR(120) NOT NULL COMMENT '执行适配器模板ID',
  `template_name` VARCHAR(160) NOT NULL COMMENT '模板名称',
  `adapter_type` VARCHAR(48) NOT NULL COMMENT '适配器类型',
  `supported_actions_json` TEXT NOT NULL COMMENT '支持动作',
  `default_config_json` LONGTEXT NOT NULL COMMENT '默认配置',
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `read_only` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否只读',
  `description` TEXT NULL COMMENT '说明',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `create_by` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adapter_template_id` (`adapter_template_id`),
  KEY `idx_adapter_status` (`adapter_type`, `status`),
  KEY `idx_risk_status` (`risk_level`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行适配器模板表';

SET @adapter_template_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_execution_resource'
    AND COLUMN_NAME = 'adapter_template_id'
);
SET @adapter_template_col_sql := IF(
  @adapter_template_col_exists = 0,
  'ALTER TABLE `ai_ops_execution_resource` ADD COLUMN `adapter_template_id` VARCHAR(120) NOT NULL DEFAULT '''' COMMENT ''来源执行适配器模板ID'' AFTER `adapter`',
  'SELECT 1'
);
PREPARE adapter_template_col_stmt FROM @adapter_template_col_sql;
EXECUTE adapter_template_col_stmt;
DEALLOCATE PREPARE adapter_template_col_stmt;
