CREATE TABLE IF NOT EXISTS `ai_ops_change_dry_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dry_run_id` VARCHAR(80) NOT NULL COMMENT '预演ID',
  `plan_id` VARCHAR(80) NOT NULL COMMENT '变更计划ID',
  `plan_hash` VARCHAR(80) NOT NULL COMMENT '预演绑定计划哈希',
  `project_id` VARCHAR(80) NOT NULL COMMENT '项目ID',
  `environment` VARCHAR(32) NOT NULL COMMENT '环境',
  `status` VARCHAR(32) NOT NULL COMMENT 'RUNNING/PASSED/FAILED',
  `requested_by` VARCHAR(120) NOT NULL COMMENT '发起人',
  `summary` VARCHAR(500) NULL COMMENT '预演摘要',
  `actions_json` MEDIUMTEXT NOT NULL COMMENT '动作级预演证据',
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `completed_at` TIMESTAMP NULL COMMENT '完成时间',
  `duration_ms` BIGINT NULL COMMENT '总耗时毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dry_run_id` (`dry_run_id`),
  KEY `idx_plan_time` (`plan_id`, `started_at`),
  KEY `idx_plan_hash_status` (`plan_hash`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控变更只读预演';
