-- Phase 069: target-side idempotency, CAS and fencing receipt for local MySQL Landing.
-- One execution_key is consumed at most once. The target update and receipt are
-- committed in the same mysqlTransactionManager transaction.

CREATE TABLE IF NOT EXISTS `ai_ops_mysql_landing_receipt` (
  `execution_key` VARCHAR(160) NOT NULL COMMENT 'Stable Landing operation execution key',
  `fencing_token` BIGINT NOT NULL COMMENT 'Authoritative Landing operation fencing token',
  `status` VARCHAR(32) NOT NULL COMMENT 'SUCCEEDED or BLOCKED',
  `reason_code` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Stable machine-readable result reason',
  `affected_rows` INT NOT NULL DEFAULT 0 COMMENT 'Target CAS affected rows',
  `table_name` VARCHAR(128) NOT NULL COMMENT 'Approved target table',
  `key_value` VARCHAR(512) NOT NULL COMMENT 'Approved target business key',
  `expected_value` TEXT NOT NULL COMMENT 'Approved expected value used by CAS',
  `expected_version` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Approved expected version used by CAS',
  `new_value` TEXT NOT NULL COMMENT 'Approved target value',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`execution_key`),
  KEY `idx_mysql_landing_receipt_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Target-side authoritative receipt for local MySQL Landing';
