SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_plan' AND COLUMN_NAME = 'proposal_source') = 0,
  'ALTER TABLE `ai_ops_change_plan` ADD COLUMN `proposal_source` VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''提案来源'' AFTER `requester`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_plan' AND COLUMN_NAME = 'source_run_id') = 0,
  'ALTER TABLE `ai_ops_change_plan` ADD COLUMN `source_run_id` VARCHAR(100) NULL COMMENT ''Agent运行、告警或沙箱工作区ID'' AFTER `proposal_source`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_plan' AND COLUMN_NAME = 'diagnosis') = 0,
  'ALTER TABLE `ai_ops_change_plan` ADD COLUMN `diagnosis` TEXT NULL COMMENT ''产生变更提案的诊断结论'' AFTER `source_run_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_plan' AND COLUMN_NAME = 'evidence_json') = 0,
  'ALTER TABLE `ai_ops_change_plan` ADD COLUMN `evidence_json` MEDIUMTEXT NULL COMMENT ''不可变证据引用快照'' AFTER `diagnosis`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `ai_ops_change_control` (
  `project_id` VARCHAR(80) NOT NULL COMMENT '项目ID，__GLOBAL__ 表示全局默认',
  `kill_switch` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '动态执行熔断开关',
  `kill_switch_reason` VARCHAR(500) NULL COMMENT '熔断原因',
  `change_window_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用变更窗口',
  `timezone` VARCHAR(80) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '变更窗口时区',
  `allowed_days` VARCHAR(100) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY' COMMENT '允许星期',
  `window_start` VARCHAR(8) NOT NULL DEFAULT '09:00' COMMENT '每日开始时间',
  `window_end` VARCHAR(8) NOT NULL DEFAULT '18:00' COMMENT '每日结束时间',
  `max_concurrent_plans` INT NOT NULL DEFAULT 1 COMMENT '项目最大并发变更计划数',
  `max_actions_per_plan` INT NOT NULL DEFAULT 20 COMMENT '单计划最大动作数',
  `max_limited_update_rows` INT NOT NULL DEFAULT 1000 COMMENT '有限更新最大行数',
  `allowed_action_types` VARCHAR(1000) NOT NULL DEFAULT 'MYSQL_CREATE_INDEX,MYSQL_UPDATE_LIMITED,MYSQL_SET_GLOBAL_VARIABLE,REDIS_DELETE_KEYS,REDIS_UPDATE_TTL,REDIS_CONFIG_SET,RABBITMQ_UPSERT_POLICY,SERVICE_RESTART,SERVICE_SCALE,ARTIFACT_DEPLOY' COMMENT '项目允许动作类型',
  `updated_by` VARCHAR(120) NOT NULL DEFAULT 'system' COMMENT '最后修改人',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控变更动态控制策略';

INSERT IGNORE INTO `ai_ops_change_control`
(`project_id`, `kill_switch`, `kill_switch_reason`, `updated_by`)
VALUES ('__GLOBAL__', 1, '首次启用时默认保持关闭执行', 'system');

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND COLUMN_NAME = 'task_kind') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD COLUMN `task_kind` VARCHAR(20) NOT NULL DEFAULT ''FORWARD'' AFTER `status`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND COLUMN_NAME = 'source_task_id') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD COLUMN `source_task_id` VARCHAR(80) NULL AFTER `task_kind`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND COLUMN_NAME = 'cancel_requested') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD COLUMN `cancel_requested` TINYINT(1) NOT NULL DEFAULT 0 AFTER `lease_expires_at`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND COLUMN_NAME = 'canceled_by') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD COLUMN `canceled_by` VARCHAR(120) NULL AFTER `cancel_requested`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND COLUMN_NAME = 'cancel_reason') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD COLUMN `cancel_reason` VARCHAR(500) NULL AFTER `canceled_by`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND COLUMN_NAME = 'canceled_at') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD COLUMN `canceled_at` TIMESTAMP NULL AFTER `cancel_reason`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task') = 1 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_execution_task' AND INDEX_NAME = 'idx_task_kind_status') = 0,
  'ALTER TABLE `ai_ops_change_execution_task` ADD INDEX `idx_task_kind_status` (`task_kind`, `status`, `create_time`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_verification' AND INDEX_NAME = 'uk_plan_hash_verification') > 0,
  'ALTER TABLE `ai_ops_change_verification` DROP INDEX `uk_plan_hash_verification`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_verification' AND INDEX_NAME = 'idx_plan_hash_verification') = 0,
  'ALTER TABLE `ai_ops_change_verification` ADD INDEX `idx_plan_hash_verification` (`plan_id`, `plan_hash`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_event' AND COLUMN_NAME = 'previous_hash') = 0,
  'ALTER TABLE `ai_ops_change_event` ADD COLUMN `previous_hash` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `payload_json`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_change_event' AND COLUMN_NAME = 'event_hash') = 0,
  'ALTER TABLE `ai_ops_change_event` ADD COLUMN `event_hash` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `previous_hash`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `ai_ops_change_notification_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(80) NOT NULL,
  `plan_id` VARCHAR(80) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `payload_json` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` TEXT NULL,
  `delivered_at` TIMESTAMP NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_change_notification_event` (`event_id`),
  KEY `idx_change_notification_dispatch` (`status`, `next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控变更通知 Outbox';

CREATE TABLE IF NOT EXISTS `ai_ops_change_worker_status` (
  `worker_id` VARCHAR(120) NOT NULL,
  `capabilities_json` TEXT NOT NULL,
  `current_task_id` VARCHAR(80) NULL,
  `worker_status` VARCHAR(20) NOT NULL,
  `last_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`worker_id`),
  KEY `idx_worker_last_seen` (`last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控变更 Worker 心跳状态';

CREATE TABLE IF NOT EXISTS `ai_ops_change_project_guard` (
  `project_id` VARCHAR(80) NOT NULL COMMENT '项目ID',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目级受控变更并发串行锁';

CREATE TABLE IF NOT EXISTS `ai_ops_change_role_binding` (
  `project_id` VARCHAR(80) NOT NULL,
  `environment` VARCHAR(32) NOT NULL,
  `username` VARCHAR(120) NOT NULL,
  `role_name` VARCHAR(32) NOT NULL,
  `granted_by` VARCHAR(120) NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`project_id`, `environment`, `username`, `role_name`),
  KEY `idx_change_role_user` (`username`, `role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目环境级受控变更角色绑定';
