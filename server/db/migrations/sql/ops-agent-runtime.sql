CREATE TABLE IF NOT EXISTS `ai_ops_agent_definition` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
  `name` VARCHAR(128) NULL COMMENT 'Agent名称',
  `project_id` VARCHAR(80) NULL COMMENT '所属业务系统ID',
  `engine` VARCHAR(64) NULL COMMENT '运行引擎',
  `description` TEXT NULL COMMENT '说明',
  `instruction` MEDIUMTEXT NULL COMMENT '全局指令',
  `start_node_id` VARCHAR(80) NULL COMMENT '起始节点',
  `definition_json` MEDIUMTEXT NOT NULL COMMENT 'Agent定义JSON',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
  `lifecycle` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期',
  `source` VARCHAR(32) NOT NULL DEFAULT 'YAML' COMMENT '来源',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_engine` (`engine`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent编排定义表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_definition_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
  `version` INT NOT NULL COMMENT '版本号',
  `lifecycle` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期',
  `name` VARCHAR(128) NULL COMMENT 'Agent名称',
  `project_id` VARCHAR(80) NULL COMMENT '所属业务系统ID',
  `engine` VARCHAR(64) NULL COMMENT '运行引擎',
  `description` TEXT NULL COMMENT '说明',
  `instruction` MEDIUMTEXT NULL COMMENT '全局指令',
  `start_node_id` VARCHAR(80) NULL COMMENT '起始节点',
  `definition_json` MEDIUMTEXT NOT NULL COMMENT 'Agent定义JSON快照',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '历史版本是否可回放',
  `current_published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前发布版本',
  `source` VARCHAR(32) NOT NULL DEFAULT 'UI' COMMENT '来源',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version` (`agent_id`, `version`),
  KEY `idx_agent_current` (`agent_id`, `current_published`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent定义历史版本表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
  `node_id` VARCHAR(80) NOT NULL COMMENT '节点ID',
  `node_type` VARCHAR(48) NOT NULL COMMENT '节点类型',
  `agent` VARCHAR(128) NULL COMMENT '节点Agent',
  `sub_engine` VARCHAR(64) NULL COMMENT '子引擎',
  `output_key` VARCHAR(80) NULL COMMENT '输出Key',
  `rag_enabled` TINYINT NULL COMMENT '是否启用RAG',
  `knowledge_base_id` VARCHAR(128) NULL COMMENT '知识库ID',
  `description` TEXT NULL COMMENT '节点说明',
  `instruction` MEDIUMTEXT NULL COMMENT '节点指令',
  `config_json` TEXT NULL COMMENT '节点配置JSON',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_node` (`agent_id`, `node_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_node_type` (`node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent节点表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_edge` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
  `from_node_id` VARCHAR(80) NOT NULL COMMENT '起点节点',
  `to_node_id` VARCHAR(80) NOT NULL COMMENT '终点节点',
  `condition_expr` VARCHAR(512) NOT NULL DEFAULT 'always' COMMENT '条件表达式',
  `description` TEXT NULL COMMENT '连线说明',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_from_node` (`agent_id`, `from_node_id`),
  KEY `idx_to_node` (`agent_id`, `to_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent连线表';

CREATE TABLE IF NOT EXISTS `ai_ops_agentscope_agent` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT '所属Agent定义ID',
  `scope_agent_id` VARCHAR(128) NULL COMMENT 'AgentScope子Agent ID',
  `name` VARCHAR(128) NULL COMMENT '名称',
  `instruction` MEDIUMTEXT NULL COMMENT '指令',
  `output_key` VARCHAR(80) NULL COMMENT '输出Key',
  `rag_enabled` TINYINT NULL COMMENT '是否启用RAG',
  `knowledge_base_id` VARCHAR(128) NULL COMMENT '知识库ID',
  `max_iterations` INT NULL COMMENT '最大循环次数',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维AgentScope子Agent表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_skill_binding` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
  `owner_type` VARCHAR(32) NOT NULL COMMENT '绑定对象类型：AGENT/NODE/AGENTSCOPE',
  `owner_id` VARCHAR(128) NOT NULL COMMENT '绑定对象ID',
  `skill_name` VARCHAR(128) NOT NULL COMMENT 'Skill名称',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_binding` (`agent_id`, `owner_type`, `owner_id`, `skill_name`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_skill_name` (`skill_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent Skill绑定表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_mcp_server` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
  `owner_type` VARCHAR(32) NOT NULL COMMENT '绑定对象类型：AGENT/NODE/AGENTSCOPE',
  `owner_id` VARCHAR(128) NOT NULL COMMENT '绑定对象ID',
  `server_name` VARCHAR(128) NOT NULL COMMENT 'MCP Server名称',
  `description` TEXT NULL COMMENT '说明',
  `transport` VARCHAR(32) NOT NULL DEFAULT 'stdio' COMMENT '传输协议',
  `command_text` TEXT NULL COMMENT 'stdio命令',
  `url` VARCHAR(512) NULL COMMENT 'SSE/HTTP URL',
  `timeout_seconds` INT NULL COMMENT '超时秒数',
  `args_json` TEXT NULL COMMENT '命令参数JSON',
  `env_json` TEXT NULL COMMENT '环境变量JSON',
  `headers_json` TEXT NULL COMMENT 'HTTP请求头JSON',
  `tool_capabilities_json` TEXT NULL COMMENT '工具能力JSON',
  `allowed_tools_json` TEXT NULL COMMENT '只读允许工具JSON',
  `notification_tools_json` TEXT NULL COMMENT '通知工具JSON',
  `blocked_tools_json` TEXT NULL COMMENT '禁用工具JSON',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_owner` (`agent_id`, `owner_type`, `owner_id`),
  KEY `idx_transport` (`transport`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent MCP绑定表';

CREATE TABLE IF NOT EXISTS `ai_ops_project` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `project_id` VARCHAR(80) NOT NULL COMMENT '业务系统ID',
  `name` VARCHAR(128) NOT NULL COMMENT '业务系统名称',
  `description` TEXT NULL COMMENT '说明',
  `owner` VARCHAR(80) NULL COMMENT '负责人',
  `environments_json` TEXT NULL COMMENT '环境列表JSON',
  `knowledge_base_id` VARCHAR(128) NULL COMMENT '默认知识库ID',
  `default_agent_id` VARCHAR(128) NOT NULL DEFAULT 'generic-ops-react-agent' COMMENT '项目默认Agent',
  `skill_ids_json` TEXT NULL COMMENT '项目允许使用的Skill ID列表',
  `shared_mcp_ids_json` TEXT NULL COMMENT '项目绑定的通用MCP ID列表',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_id` (`project_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维业务系统空间表';

CREATE TABLE IF NOT EXISTS `ai_ops_project_resource` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `resource_id` VARCHAR(128) NOT NULL COMMENT '资源ID',
  `project_id` VARCHAR(80) NOT NULL COMMENT '业务系统ID',
  `resource_type` VARCHAR(48) NOT NULL COMMENT '资源类型',
  `type_name` VARCHAR(80) NULL COMMENT '资源类型名称',
  `name` VARCHAR(128) NOT NULL COMMENT '资源名称',
  `environment` VARCHAR(32) NOT NULL DEFAULT 'prod' COMMENT '环境',
  `endpoint` VARCHAR(512) NOT NULL COMMENT '连接地址',
  `credential_json` TEXT NULL COMMENT '凭据JSON',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PREVIEW' COMMENT '扫描状态',
  `schema_json` MEDIUMTEXT NULL COMMENT '对象结构JSON',
  `permission_json` MEDIUMTEXT NULL COMMENT '权限策略JSON',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_id` (`resource_id`),
  KEY `idx_project_type` (`project_id`, `resource_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维业务系统资源表';

CREATE TABLE IF NOT EXISTS `ai_ops_project_mcp` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `mcp_id` VARCHAR(128) NOT NULL COMMENT '项目级MCP ID',
  `mcp_name` VARCHAR(160) NOT NULL COMMENT 'MCP名称',
  `project_id` VARCHAR(80) NOT NULL COMMENT '业务系统ID',
  `resource_id` VARCHAR(128) NOT NULL COMMENT '资源ID',
  `resource_type` VARCHAR(48) NOT NULL COMMENT '资源类型',
  `transport_type` VARCHAR(32) NOT NULL DEFAULT 'stdio' COMMENT '传输类型',
  `transport_config_json` MEDIUMTEXT NULL COMMENT '脱敏传输配置JSON',
  `request_timeout` INT NOT NULL DEFAULT 30 COMMENT '超时秒',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_id` (`mcp_id`),
  KEY `idx_project_resource` (`project_id`, `resource_id`),
  KEY `idx_resource_type` (`resource_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维项目级MCP表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_node_trace` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `run_id` VARCHAR(80) NULL COMMENT '异步分析任务ID',
  `analysis_id` VARCHAR(80) NULL COMMENT '分析ID',
  `sequence_no` BIGINT NOT NULL COMMENT '事件序号',
  `event_type` VARCHAR(128) NOT NULL COMMENT '事件类型',
  `node_id` VARCHAR(80) NULL COMMENT 'Graph节点ID',
  `node_type` VARCHAR(48) NULL COMMENT 'Graph节点类型',
  `agent` VARCHAR(80) NULL COMMENT 'Agent',
  `source` VARCHAR(80) NULL COMMENT '数据源',
  `status` VARCHAR(32) NULL COMMENT '节点状态',
  `summary` TEXT NULL COMMENT '节点摘要',
  `started_at` VARCHAR(32) NULL COMMENT '开始时间',
  `finished_at` VARCHAR(32) NULL COMMENT '结束时间',
  `duration_ms` BIGINT NULL COMMENT '耗时毫秒',
  `payload_json` TEXT NULL COMMENT '扩展上下文',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_analysis_id` (`analysis_id`),
  KEY `idx_node_id` (`node_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent Graph节点事件与审计表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_audit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `analysis_id` VARCHAR(64) NOT NULL COMMENT '分析ID',
  `success` TINYINT NOT NULL COMMENT '是否成功',
  `question` TEXT NULL COMMENT '用户问题',
  `intent` VARCHAR(64) NULL COMMENT '主Agent意图',
  `range_minutes` INT NULL COMMENT '日志时间窗口',
  `prom_window` VARCHAR(16) NULL COMMENT 'Prometheus rate窗口',
  `generated_at` VARCHAR(32) NULL COMMENT '分析生成时间',
  `duration_ms` BIGINT NULL COMMENT '耗时毫秒',
  `selected_sources_json` TEXT NULL COMMENT '计划选择数据源',
  `executed_sources_json` TEXT NULL COMMENT '实际执行数据源',
  `skipped_sources_json` TEXT NULL COMMENT '跳过数据源',
  `result_statuses_json` TEXT NULL COMMENT '子Agent结果状态',
  `insight_levels_json` TEXT NULL COMMENT '规则洞察等级',
  `conclusion` TEXT NULL COMMENT '首要结论',
  `error_message` TEXT NULL COMMENT '失败原因',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_id` (`analysis_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例运维AI分析审计表';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `run_id` VARCHAR(80) NOT NULL COMMENT '运行ID',
  `status` VARCHAR(32) NOT NULL COMMENT '运行状态',
  `request_json` MEDIUMTEXT NULL COMMENT '请求JSON',
  `response_json` MEDIUMTEXT NULL COMMENT '响应JSON',
  `error_message` TEXT NULL COMMENT '失败原因',
  `created_at` VARCHAR(32) NOT NULL COMMENT '创建时间',
  `updated_at` VARCHAR(32) NOT NULL COMMENT '更新时间',
  `duration_ms` BIGINT NULL COMMENT '耗时毫秒',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_status` (`status`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例运维AI异步运行记录表';

CREATE TABLE IF NOT EXISTS `ai_ops_alert_trigger_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'ALERTMANAGER' COMMENT '来源类型',
  `alert_name_regex` VARCHAR(255) NULL COMMENT 'alertname 正则',
  `severity_regex` VARCHAR(255) NULL COMMENT 'severity 正则',
  `service_regex` VARCHAR(255) NULL COMMENT 'service/app/job 正则',
  `match_labels_json` TEXT NULL COMMENT '标签匹配 JSON，值以 ~ 开头表示正则',
  `receiver` VARCHAR(160) NULL COMMENT 'Weixin 接收人',
  `webhook_secret` VARCHAR(160) NULL COMMENT 'Webhook签名密钥',
  `project_id` VARCHAR(80) NOT NULL COMMENT '业务系统ID',
  `agent_definition_id` VARCHAR(128) NULL COMMENT 'Agent 定义 ID',
  `question_template` TEXT NULL COMMENT '问题模板',
  `range_minutes` INT NOT NULL DEFAULT 15 COMMENT '分析窗口',
  `prom_window` VARCHAR(16) NOT NULL DEFAULT '5m' COMMENT 'Prometheus 窗口',
  `include_recent_logs` TINYINT NOT NULL DEFAULT 1 COMMENT '是否查最近日志',
  `notify_weixin` TINYINT NOT NULL DEFAULT 1 COMMENT '是否微信推送',
  `sub_agent_max_iterations` INT NULL COMMENT '子 Agent 循环上限',
  `node_timeout_seconds` INT NULL COMMENT '节点超时秒',
  `max_evidence_items` INT NULL COMMENT '证据条数上限',
  `dedup_window_seconds` INT NOT NULL DEFAULT 900 COMMENT '去重窗口秒',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_source` (`status`, `source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发规则表';

CREATE TABLE IF NOT EXISTS `ai_ops_alert_trigger_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` BIGINT NULL COMMENT '规则ID',
  `rule_name` VARCHAR(128) NULL COMMENT '规则名称',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型',
  `status` VARCHAR(32) NOT NULL COMMENT '处理状态',
  `dedup_key` VARCHAR(128) NULL COMMENT '幂等去重键',
  `fingerprint` VARCHAR(160) NOT NULL COMMENT '告警指纹',
  `alert_name` VARCHAR(160) NULL COMMENT '告警名',
  `severity` VARCHAR(64) NULL COMMENT '级别',
  `service_name` VARCHAR(160) NULL COMMENT '服务名',
  `receiver` VARCHAR(160) NULL COMMENT '接收人',
  `run_id` VARCHAR(80) NULL COMMENT '异步分析任务ID',
  `run_status` VARCHAR(32) NULL COMMENT '异步分析最终状态',
  `final_summary` TEXT NULL COMMENT '最终摘要',
  `completed_at` TIMESTAMP NULL COMMENT '完成时间',
  `error_message` TEXT NULL COMMENT '错误信息',
  `labels_json` TEXT NULL COMMENT '告警标签',
  `annotations_json` TEXT NULL COMMENT '告警注解',
  `payload_json` MEDIUMTEXT NULL COMMENT '原始告警',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_fingerprint_time` (`rule_id`, `fingerprint`, `create_time`),
  KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发事件表';

CREATE TABLE IF NOT EXISTS `ai_ops_alert_trigger_dedup` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dedup_key` VARCHAR(128) NOT NULL COMMENT '幂等去重键',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `fingerprint` VARCHAR(160) NOT NULL COMMENT '告警指纹',
  `acquired_token` VARCHAR(64) NOT NULL COMMENT '本轮获取令牌',
  `run_id` VARCHAR(80) NULL COMMENT '异步分析任务ID',
  `window_until` TIMESTAMP NOT NULL COMMENT '去重窗口截止时间',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_rule_fingerprint` (`rule_id`, `fingerprint`),
  KEY `idx_window_until` (`window_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发幂等表';

CREATE TABLE IF NOT EXISTS `ai_ops_alert_trigger_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dedup_key` VARCHAR(128) NOT NULL COMMENT '幂等去重键',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `fingerprint` VARCHAR(160) NOT NULL COMMENT '告警指纹',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/RUNNING/SUCCEEDED/FAILED',
  `run_id` VARCHAR(80) NULL COMMENT '异步分析任务ID',
  `request_json` MEDIUMTEXT NOT NULL COMMENT '待提交分析请求',
  `payload_json` MEDIUMTEXT NULL COMMENT '原始告警',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` TIMESTAMP NULL COMMENT '下次重试时间',
  `locked_token` VARCHAR(64) NULL COMMENT '处理锁令牌',
  `error_message` TEXT NULL COMMENT '错误信息',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_status_retry` (`status`, `next_retry_at`),
  KEY `idx_rule_fingerprint` (`rule_id`, `fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发 Outbox 表';

CREATE TABLE IF NOT EXISTS `ai_ops_incident` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `incident_id` VARCHAR(80) NOT NULL COMMENT '故障事件ID',
  `title` VARCHAR(240) NOT NULL COMMENT '事件标题',
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACKED/INVESTIGATING/MITIGATED/RESOLVED/REVIEWED',
  `severity` VARCHAR(32) NOT NULL DEFAULT 'WARN' COMMENT '严重级别',
  `service_name` VARCHAR(160) NULL COMMENT '服务名',
  `source_type` VARCHAR(64) NULL COMMENT '来源类型',
  `fingerprint` VARCHAR(160) NULL COMMENT '告警指纹',
  `dedup_key` VARCHAR(160) NULL COMMENT '去重键',
  `current_run_id` VARCHAR(80) NULL COMMENT '最近分析run',
  `summary` TEXT NULL COMMENT '事件摘要',
  `labels_json` TEXT NULL COMMENT '标签JSON',
  `metadata_json` TEXT NULL COMMENT '扩展JSON',
  `acknowledged_at` TIMESTAMP NULL COMMENT '确认时间',
  `resolved_at` TIMESTAMP NULL COMMENT '恢复时间',
  `reviewed_at` TIMESTAMP NULL COMMENT '复盘时间',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_incident_id` (`incident_id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_status_update` (`status`, `update_time`),
  KEY `idx_fingerprint` (`fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维故障事件表';

CREATE TABLE IF NOT EXISTS `ai_ops_incident_timeline` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `incident_id` VARCHAR(80) NOT NULL COMMENT '故障事件ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `title` VARCHAR(240) NOT NULL COMMENT '标题',
  `detail` MEDIUMTEXT NULL COMMENT '详情',
  `actor` VARCHAR(120) NULL COMMENT '操作者',
  `ref_type` VARCHAR(64) NULL COMMENT '关联类型',
  `ref_id` VARCHAR(160) NULL COMMENT '关联ID',
  `payload_json` MEDIUMTEXT NULL COMMENT '扩展JSON',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_incident_time` (`incident_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维故障事件时间线';

CREATE TABLE IF NOT EXISTS `ai_ops_incident_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `incident_id` VARCHAR(80) NOT NULL COMMENT '故障事件ID',
  `run_id` VARCHAR(80) NOT NULL COMMENT '分析run',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_incident_run` (`incident_id`, `run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障事件与分析run关联表';

CREATE TABLE IF NOT EXISTS `ai_ops_chat_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `session_id` VARCHAR(80) NOT NULL COMMENT '会话ID',
  `user_id` VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
  `project_id` VARCHAR(80) DEFAULT NULL COMMENT '业务系统ID',
  `agent_id` VARCHAR(128) DEFAULT NULL COMMENT 'Agent定义ID',
  `agent_version` INT NULL COMMENT 'Agent定义版本',
  `title` VARCHAR(180) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
  `mode` VARCHAR(32) NOT NULL DEFAULT 'MULTI_TURN' COMMENT '会话模式',
  `engine` VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT '运行引擎',
  `rag_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用RAG',
  `knowledge_base_id` VARCHAR(128) DEFAULT NULL COMMENT '知识库ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `metadata` TEXT COMMENT '扩展信息',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_active_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_agent` (`user_id`, `agent_id`),
  KEY `idx_last_active` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用Agent会话表';

CREATE TABLE IF NOT EXISTS `ai_ops_chat_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `session_id` VARCHAR(80) NOT NULL COMMENT '会话ID',
  `user_id` VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
  `role` VARCHAR(32) NOT NULL COMMENT '消息角色',
  `content` MEDIUMTEXT NOT NULL COMMENT '消息内容',
  `metadata` TEXT COMMENT '扩展信息',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent对话记忆表';

CREATE TABLE IF NOT EXISTS `ai_ops_memory_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `session_id` VARCHAR(80) NOT NULL COMMENT '会话ID',
  `user_id` VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
  `memory_type` VARCHAR(32) NOT NULL COMMENT '记忆类型：fact/summary/preference/task',
  `content` MEDIUMTEXT NOT NULL COMMENT '记忆内容',
  `importance` DECIMAL(5,4) NOT NULL DEFAULT 0.5000 COMMENT '重要度',
  `tags_json` TEXT COMMENT '标签JSON',
  `source_message_role` VARCHAR(32) DEFAULT NULL COMMENT '来源消息角色',
  `source_message_hash` VARCHAR(64) DEFAULT NULL COMMENT '来源消息哈希',
  `metadata` TEXT COMMENT '扩展信息',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_type` (`session_id`, `memory_type`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_importance` (`importance`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent长期记忆条目表';

CREATE TABLE IF NOT EXISTS `ai_rag_ingestion_job` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `job_id` VARCHAR(80) NOT NULL COMMENT 'RAG入库任务ID',
  `status` VARCHAR(32) NOT NULL COMMENT '任务状态',
  `name` VARCHAR(128) NOT NULL COMMENT '知识库名称',
  `tag` VARCHAR(128) NOT NULL COMMENT '知识标签',
  `file_names_json` TEXT NULL COMMENT '文件名列表',
  `total_bytes` BIGINT NULL COMMENT '文件总大小',
  `error_message` TEXT NULL COMMENT '失败原因',
  `created_at` VARCHAR(32) NOT NULL COMMENT '创建时间',
  `updated_at` VARCHAR(32) NOT NULL COMMENT '更新时间',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG异步入库任务表';

CREATE TABLE IF NOT EXISTS `ai_rag_feedback` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `query_text` TEXT NOT NULL COMMENT '查询问题',
  `answer_text` MEDIUMTEXT NULL COMMENT '回答内容',
  `useful` TINYINT NULL COMMENT '是否有用',
  `resolved` TINYINT NULL COMMENT '是否解决问题',
  `source_type` VARCHAR(64) NULL COMMENT '来源类型',
  `source_id` VARCHAR(128) NULL COMMENT '来源ID',
  `knowledge_tag` VARCHAR(128) NULL COMMENT '知识库标签',
  `chunk_ids_json` TEXT NULL COMMENT '命中chunk ID',
  `comment_text` TEXT NULL COMMENT '反馈说明',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tag_time` (`knowledge_tag`, `create_time`),
  KEY `idx_useful_resolved` (`useful`, `resolved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 检索反馈表';

CREATE TABLE IF NOT EXISTS `ai_rag_knowledge_gap` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `gap_key` VARCHAR(64) NOT NULL COMMENT '缺口去重键',
  `query_text` TEXT NOT NULL COMMENT '样例问题',
  `knowledge_tag` VARCHAR(128) NULL COMMENT '知识库标签',
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/TRIAGED/FIXED/IGNORED',
  `feedback_count` INT NOT NULL DEFAULT 1 COMMENT '反馈次数',
  `last_feedback_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近反馈时间',
  `sample_comment` TEXT NULL COMMENT '样例说明',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gap_key` (`gap_key`),
  KEY `idx_status_update` (`status`, `update_time`),
  KEY `idx_tag_status` (`knowledge_tag`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识缺口表';
