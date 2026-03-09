ALTER TABLE `ai_ops_channel_message`
  ADD COLUMN `processing_token` VARCHAR(80) NOT NULL DEFAULT '' AFTER `error_message`,
  ADD COLUMN `lease_expires_at` DATETIME(3) NULL AFTER `processing_token`,
  ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0 AFTER `lease_expires_at`;

CREATE INDEX `idx_channel_message_inbound_lease`
  ON `ai_ops_channel_message` (`direction`, `status`, `lease_expires_at`);

CREATE TABLE IF NOT EXISTS `ai_ops_channel_conversation_lease` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel_id` VARCHAR(80) NOT NULL,
  `external_conversation_id` VARCHAR(256) NOT NULL,
  `sender_id` VARCHAR(256) NOT NULL,
  `lock_token` VARCHAR(80) NOT NULL,
  `lease_expires_at` DATETIME(3) NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_conversation_lease` (`channel_id`, `external_conversation_id`, `sender_id`),
  KEY `idx_channel_conversation_lease_expiry` (`lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel跨实例会话顺序租约';
