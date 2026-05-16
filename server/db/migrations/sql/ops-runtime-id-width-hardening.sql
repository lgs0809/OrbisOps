-- Runtime ID width hardening discovered by real local browser execution.
-- Keep this as a new migration so historical migration checksums do not drift.

ALTER TABLE `ai_ops_config_audit`
  MODIFY COLUMN `target_id` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '目标ID';

ALTER TABLE `ai_ops_mcp_tool_policy`
  MODIFY COLUMN `policy_id` VARCHAR(256) NOT NULL;
