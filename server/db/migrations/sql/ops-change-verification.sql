CREATE TABLE IF NOT EXISTS `ai_ops_change_verification` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `verification_id` VARCHAR(80) NOT NULL COMMENT '验证记录ID',
  `plan_id` VARCHAR(80) NOT NULL COMMENT '变更计划ID',
  `plan_hash` VARCHAR(80) NOT NULL COMMENT '验证绑定计划哈希',
  `status` VARCHAR(20) NOT NULL COMMENT 'PASSED/FAILED',
  `verifier_type` VARCHAR(20) NOT NULL COMMENT 'MANUAL/AUTOMATED',
  `verifier` VARCHAR(120) NOT NULL COMMENT '验证人或验证 Worker',
  `summary` VARCHAR(1000) NOT NULL COMMENT '验证摘要',
  `evidence_json` MEDIUMTEXT NOT NULL COMMENT '验证证据',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verification_id` (`verification_id`),
  UNIQUE KEY `uk_plan_hash_verification` (`plan_id`, `plan_hash`),
  KEY `idx_verification_plan` (`plan_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控变更结果验证';
