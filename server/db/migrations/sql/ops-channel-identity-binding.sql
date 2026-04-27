CREATE TABLE IF NOT EXISTS `ai_ops_channel_identity` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `mapping_id` VARCHAR(80) NOT NULL,
  `channel_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(128) NOT NULL,
  `external_sender_id` VARCHAR(256) NOT NULL,
  `platform_user_id` VARCHAR(128) NOT NULL,
  `username` VARCHAR(128) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `version` BIGINT NOT NULL DEFAULT 1,
  `created_by` VARCHAR(128) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_identity_mapping` (`mapping_id`),
  UNIQUE KEY `uk_channel_identity_sender` (`channel_id`, `external_sender_id`),
  KEY `idx_channel_identity_project` (`project_id`, `channel_id`, `status`),
  KEY `idx_channel_identity_user` (`project_id`, `platform_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部Channel发送者到平台用户的受控绑定';
