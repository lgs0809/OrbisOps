CREATE TABLE IF NOT EXISTS `ai_ops_agent_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `run_id` VARCHAR(80) NOT NULL COMMENT '异步分析任务ID',
  `status` VARCHAR(32) NOT NULL COMMENT '任务状态',
  `request_json` TEXT NULL COMMENT '请求JSON',
  `response_json` MEDIUMTEXT NULL COMMENT '响应JSON',
  `error_message` TEXT NULL COMMENT '错误信息',
  `created_at` VARCHAR(32) NOT NULL COMMENT '创建时间',
  `updated_at` VARCHAR(32) NOT NULL COMMENT '更新时间',
  `duration_ms` BIGINT NULL COMMENT '耗时毫秒',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例运维AI异步分析任务表';
