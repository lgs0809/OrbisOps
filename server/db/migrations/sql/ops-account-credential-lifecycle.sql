-- Existing accounts keep their password, role and active sessions at version 0.
-- Initial credentials are BCrypt hashes populated only by normal account creation.
DROP PROCEDURE IF EXISTS ops_account_credential_lifecycle_103;
DELIMITER $$
CREATE PROCEDURE ops_account_credential_lifecycle_103()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_user') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ADMIN_USER_SCHEMA_REQUIRED';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_user' AND COLUMN_NAME='initial_password') THEN
    ALTER TABLE admin_user ADD COLUMN initial_password VARCHAR(255) NULL COMMENT 'Hashed initial credential; never exposed';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_user' AND COLUMN_NAME='password_change_required') THEN
    ALTER TABLE admin_user ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_user' AND COLUMN_NAME='credential_version') THEN
    ALTER TABLE admin_user ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
  END IF;
END$$
DELIMITER ;
CALL ops_account_credential_lifecycle_103();
DROP PROCEDURE ops_account_credential_lifecycle_103;
