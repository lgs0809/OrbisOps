-- 生产化增强表。项目运行时会按配置自动创建这些表；这里保留手工初始化 SQL，便于 DBA 审核。

CREATE TABLE IF NOT EXISTS `ai_ops_config_audit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `module_name` VARCHAR(64) NOT NULL COMMENT '配置模块',
  `action_name` VARCHAR(64) NOT NULL COMMENT '操作',
  `target_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '目标ID',
  `operator_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '操作者ID',
  `operator_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '操作者名称',
  `operator_role` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作者角色',
  `client_ip` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '客户端IP',
  `before_json` MEDIUMTEXT NULL COMMENT '变更前',
  `after_json` MEDIUMTEXT NULL COMMENT '变更后',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_module_time` (`module_name`, `create_time`),
  KEY `idx_target_time` (`target_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维AI配置审计表';

CREATE TABLE IF NOT EXISTS `ai_ops_weixin_notification_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `dedup_key` VARCHAR(255) NOT NULL COMMENT '幂等键',
  `analysis_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '分析ID',
  `receiver` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '接收人',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED',
  `request_json` MEDIUMTEXT NULL COMMENT '请求快照',
  `response_json` MEDIUMTEXT NULL COMMENT '响应快照',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` DATETIME NOT NULL COMMENT '下次重试时间',
  `locked_token` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '派发锁',
  `last_response` MEDIUMTEXT NULL COMMENT '最近响应',
  `last_error` TEXT NULL COMMENT '最近错误',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_status_retry` (`status`, `next_retry_at`),
  KEY `idx_analysis_id` (`analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维AI微信通知Outbox';

CREATE TABLE IF NOT EXISTS `ai_rag_eval_case` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `case_name` VARCHAR(160) NOT NULL DEFAULT '' COMMENT '用例名称',
  `query_text` TEXT NOT NULL COMMENT '查询问题',
  `knowledge_tag` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '知识库标签',
  `expected_keywords_json` TEXT NULL COMMENT '期望关键词JSON',
  `top_k` INT NOT NULL DEFAULT 8 COMMENT '检索数量',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_knowledge_tag` (`knowledge_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG离线评测用例';

CREATE TABLE IF NOT EXISTS `ai_rag_eval_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `case_count` INT NOT NULL DEFAULT 0 COMMENT '用例数',
  `hit_rate` DOUBLE NOT NULL DEFAULT 0 COMMENT '命中率',
  `average_keyword_coverage` DOUBLE NOT NULL DEFAULT 0 COMMENT '平均关键词覆盖',
  `mrr` DOUBLE NOT NULL DEFAULT 0 COMMENT 'MRR',
  `passed_count` BIGINT NOT NULL DEFAULT 0 COMMENT '通过数',
  `result_json` MEDIUMTEXT NULL COMMENT '评测结果',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG离线评测运行记录';
