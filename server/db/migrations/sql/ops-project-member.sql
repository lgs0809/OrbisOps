CREATE TABLE IF NOT EXISTS `ai_ops_project_member` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `project_id` VARCHAR(80) NOT NULL COMMENT '项目ID',
  `member_key` VARCHAR(128) NOT NULL COMMENT '稳定成员键，优先userId',
  `user_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户ID',
  `username` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户名',
  `member_role` VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '授权状态',
  `granted_by` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '授权人',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_member` (`project_id`, `member_key`),
  KEY `idx_member_user` (`user_id`, `status`),
  KEY `idx_member_name` (`username`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维项目成员授权表';
