CREATE TABLE IF NOT EXISTS `admin_auth_revoked_token` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `jwt_id` VARCHAR(64) NOT NULL COMMENT 'JWT ID',
  `subject` VARCHAR(128) NULL COMMENT 'Username',
  `expires_at` DATETIME NOT NULL COMMENT 'Token expiration time',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jwt_id` (`jwt_id`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JWT revocation registry';

-- OrbisOps does not ship a default administrator or password.
-- Enable the explicit bootstrap administrator environment variables for first-run setup.
