CREATE TABLE IF NOT EXISTS `ai_ops_change_plan` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` VARCHAR(80) NOT NULL COMMENT '变更计划ID',
  `version` INT NOT NULL DEFAULT 1 COMMENT '计划版本',
  `incident_id` VARCHAR(80) NULL COMMENT '关联故障事件',
  `project_id` VARCHAR(80) NOT NULL COMMENT '项目ID',
  `environment` VARCHAR(32) NOT NULL COMMENT '环境',
  `title` VARCHAR(240) NOT NULL COMMENT '标题',
  `summary` TEXT NOT NULL COMMENT '计划摘要',
  `requester` VARCHAR(120) NOT NULL COMMENT '提交人',
  `proposal_source` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '提案来源',
  `source_run_id` VARCHAR(100) NULL COMMENT 'Agent运行、告警或沙箱工作区ID',
  `diagnosis` TEXT NULL COMMENT '产生变更提案的诊断结论',
  `evidence_json` MEDIUMTEXT NULL COMMENT '不可变证据引用快照',
  `status` VARCHAR(40) NOT NULL COMMENT '计划状态',
  `risk_level` VARCHAR(20) NOT NULL COMMENT '风险级别',
  `required_approvals` INT NOT NULL DEFAULT 1 COMMENT '所需审批人数',
  `plan_hash` VARCHAR(80) NULL COMMENT '不可变计划哈希',
  `actions_json` MEDIUMTEXT NOT NULL COMMENT '结构化动作JSON',
  `approval_expires_at` TIMESTAMP NULL COMMENT '审批过期时间',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_id` (`plan_id`),
  KEY `idx_project_status` (`project_id`, `status`, `update_time`),
  KEY `idx_incident_id` (`incident_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控运维变更计划';

CREATE TABLE IF NOT EXISTS `ai_ops_change_approval` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_id` VARCHAR(80) NOT NULL COMMENT '审批ID',
  `plan_id` VARCHAR(80) NOT NULL COMMENT '计划ID',
  `plan_hash` VARCHAR(80) NOT NULL COMMENT '审批绑定计划哈希',
  `decision` VARCHAR(20) NOT NULL COMMENT 'APPROVE/REJECT',
  `approver` VARCHAR(120) NOT NULL COMMENT '审批人',
  `comment_text` TEXT NULL COMMENT '审批意见',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_id` (`approval_id`),
  UNIQUE KEY `uk_plan_hash_approver` (`plan_id`, `plan_hash`, `approver`),
  KEY `idx_plan_time` (`plan_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控运维变更审批';

CREATE TABLE IF NOT EXISTS `ai_ops_change_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` VARCHAR(80) NOT NULL COMMENT '事件ID',
  `plan_id` VARCHAR(80) NOT NULL COMMENT '计划ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `actor` VARCHAR(120) NOT NULL COMMENT '操作者',
  `summary` VARCHAR(500) NOT NULL COMMENT '事件摘要',
  `payload_json` MEDIUMTEXT NULL COMMENT '事件载荷',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_plan_time` (`plan_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控运维变更事件';
