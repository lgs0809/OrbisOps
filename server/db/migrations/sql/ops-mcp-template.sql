-- MCP 模板与项目工具治理字段。增量执行，不影响已有 ai_ops_project_mcp 数据。

CREATE TABLE IF NOT EXISTS `ai_ops_mcp_template` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `template_id` VARCHAR(120) NOT NULL COMMENT 'MCP模板ID',
  `template_name` VARCHAR(160) NOT NULL COMMENT '模板名称',
  `resource_type` VARCHAR(48) NOT NULL COMMENT '资源类型',
  `transport_type` VARCHAR(32) NOT NULL DEFAULT 'stdio' COMMENT '传输类型',
  `default_transport_config_json` LONGTEXT NOT NULL COMMENT '默认传输配置',
  `supported_actions_json` TEXT NOT NULL COMMENT '支持动作',
  `risk_level` VARCHAR(24) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `read_only` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否只读',
  `description` TEXT NULL COMMENT '说明',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `create_by` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_id` (`template_id`),
  KEY `idx_resource_status` (`resource_type`, `status`),
  KEY `idx_risk_status` (`risk_level`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP模板表';

SET @mcp_template_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_project_mcp'
    AND COLUMN_NAME = 'template_id'
);
SET @mcp_template_col_sql := IF(
  @mcp_template_col_exists = 0,
  'ALTER TABLE `ai_ops_project_mcp` ADD COLUMN `template_id` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''来源MCP模板ID'' AFTER `transport_type`',
  'SELECT 1'
);
PREPARE mcp_template_col_stmt FROM @mcp_template_col_sql;
EXECUTE mcp_template_col_stmt;
DEALLOCATE PREPARE mcp_template_col_stmt;

SET @mcp_allowed_actions_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_project_mcp'
    AND COLUMN_NAME = 'allowed_actions_json'
);
SET @mcp_allowed_actions_col_sql := IF(
  @mcp_allowed_actions_col_exists = 0,
  'ALTER TABLE `ai_ops_project_mcp` ADD COLUMN `allowed_actions_json` TEXT NULL COMMENT ''允许动作JSON'' AFTER `transport_config_json`',
  'SELECT 1'
);
PREPARE mcp_allowed_actions_col_stmt FROM @mcp_allowed_actions_col_sql;
EXECUTE mcp_allowed_actions_col_stmt;
DEALLOCATE PREPARE mcp_allowed_actions_col_stmt;

SET @mcp_risk_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_project_mcp'
    AND COLUMN_NAME = 'risk_level'
);
SET @mcp_risk_col_sql := IF(
  @mcp_risk_col_exists = 0,
  'ALTER TABLE `ai_ops_project_mcp` ADD COLUMN `risk_level` VARCHAR(24) NOT NULL DEFAULT ''LOW'' COMMENT ''风险等级'' AFTER `allowed_actions_json`',
  'SELECT 1'
);
PREPARE mcp_risk_col_stmt FROM @mcp_risk_col_sql;
EXECUTE mcp_risk_col_stmt;
DEALLOCATE PREPARE mcp_risk_col_stmt;

SET @mcp_readonly_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_project_mcp'
    AND COLUMN_NAME = 'read_only'
);
SET @mcp_readonly_col_sql := IF(
  @mcp_readonly_col_exists = 0,
  'ALTER TABLE `ai_ops_project_mcp` ADD COLUMN `read_only` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否只读'' AFTER `risk_level`',
  'SELECT 1'
);
PREPARE mcp_readonly_col_stmt FROM @mcp_readonly_col_sql;
EXECUTE mcp_readonly_col_stmt;
DEALLOCATE PREPARE mcp_readonly_col_stmt;

SET @mcp_permission_policy_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_ops_project_mcp'
    AND COLUMN_NAME = 'permission_policy_json'
);
SET @mcp_permission_policy_col_sql := IF(
  @mcp_permission_policy_col_exists = 0,
  'ALTER TABLE `ai_ops_project_mcp` ADD COLUMN `permission_policy_json` MEDIUMTEXT NULL COMMENT ''权限策略JSON'' AFTER `read_only`',
  'SELECT 1'
);
PREPARE mcp_permission_policy_col_stmt FROM @mcp_permission_policy_col_sql;
EXECUTE mcp_permission_policy_col_stmt;
DEALLOCATE PREPARE mcp_permission_policy_col_stmt;
