CREATE TABLE IF NOT EXISTS `ai_ops_model_default_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `project_id` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目ID，空表示全局默认',
  `default_chat_model_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Chat模型',
  `default_embedding_model_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Embedding模型',
  `default_rerank_model_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Rerank模型',
  `default_vision_model_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Vision模型',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_policy_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认模型策略';
