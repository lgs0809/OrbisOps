-- Add a new login identity without renaming accounts, changing roles, resetting
-- credentials or rewriting owner references. NULL keeps legacy accounts usable.
-- The unique key is the final guard against concurrent account creation.
DROP PROCEDURE IF EXISTS ops_admin_phone_login_102;
DELIMITER $$
CREATE PROCEDURE ops_admin_phone_login_102()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_user'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ADMIN_USER_SCHEMA_REQUIRED';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_user' AND COLUMN_NAME = 'phone'
  ) THEN
    ALTER TABLE `admin_user`
      ADD COLUMN `phone` VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NULL
      COMMENT 'Normalized mainland phone login identity; NULL for legacy accounts'
      AFTER `username`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_user' AND INDEX_NAME = 'uk_admin_user_phone'
  ) THEN
    ALTER TABLE `admin_user` ADD UNIQUE KEY `uk_admin_user_phone` (`phone`);
  END IF;
END$$
DELIMITER ;
CALL ops_admin_phone_login_102();
DROP PROCEDURE ops_admin_phone_login_102;
