# ************************************************************
# OrbisOps baseline schema
# 版本号： 20094
#
# Database: orbisops
# ************************************************************


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE='NO_AUTO_VALUE_ON_ZERO', SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE database if NOT EXISTS `orbisops` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `orbisops`;

# 转储表 admin_user
# ------------------------------------------------------------

DROP TABLE IF EXISTS `admin_user`;

CREATE TABLE `admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID（唯一标识）',
  `username` varchar(50) NOT NULL COMMENT '用户名（登录账号）',
  `password` varchar(128) NOT NULL COMMENT '密码（加密存储）',
  `user_role` varchar(32) NOT NULL DEFAULT 'admin' COMMENT '用户角色：admin/user',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用,2:锁定)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_user_role` (`user_role`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员用户表';


# 转储表 ai_agent_task_schedule
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_agent_task_schedule`;

CREATE TABLE `ai_agent_task_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` varchar(64) NOT NULL COMMENT '运维 Agent Definition ID',
  `task_name` varchar(64) DEFAULT NULL COMMENT '任务名称',
  `description` varchar(255) DEFAULT NULL COMMENT '任务描述',
  `cron_expression` varchar(50) NOT NULL COMMENT '时间表达式(如: 0/3 * * * * *)',
  `task_param` text COMMENT '任务入参配置(JSON格式)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:无效,1:有效)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体任务调度配置表';



# 转储表 ai_agent_task_execution
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_agent_task_execution`;

CREATE TABLE `ai_agent_task_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `schedule_id` bigint NOT NULL COMMENT '任务配置ID',
  `task_name` varchar(64) DEFAULT NULL COMMENT '任务名称',
  `agent_id` varchar(64) NOT NULL COMMENT 'Agent Definition ID',
  `trigger_type` varchar(32) NOT NULL COMMENT '触发方式',
  `status` varchar(16) NOT NULL COMMENT '执行状态',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间',
  `input` longtext COMMENT '执行输入',
  `output` longtext COMMENT '执行输出',
  `error_message` text COMMENT '错误信息',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_id` (`schedule_id`),
  KEY `idx_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体周期任务执行记录表';


# 转储表 ai_client
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client`;

CREATE TABLE `ai_client` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_id` varchar(64) NOT NULL COMMENT '客户端ID',
  `client_name` varchar(50) NOT NULL COMMENT '客户端名称',
  `description` varchar(1024) DEFAULT NULL COMMENT '描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客户端配置表';



# 转储表 ai_client_advisor
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_advisor`;

CREATE TABLE `ai_client_advisor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `advisor_id` varchar(64) NOT NULL COMMENT '顾问ID',
  `advisor_name` varchar(50) NOT NULL COMMENT '顾问名称',
  `advisor_type` varchar(50) NOT NULL COMMENT '顾问类型(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor等)',
  `order_num` int DEFAULT '0' COMMENT '顺序号',
  `ext_param` varchar(2048) DEFAULT NULL COMMENT '扩展参数配置，json 记录',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_advisor_id` (`advisor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='顾问配置表';



# 转储表 ai_client_api
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_api`;

CREATE TABLE `ai_client_api` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
  `api_id` varchar(64) NOT NULL COMMENT '全局唯一配置ID',
  `provider_name` varchar(128) NOT NULL DEFAULT '' COMMENT 'Provider 名称',
  `provider_type` varchar(64) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'Provider 类型',
  `base_url` varchar(255) NOT NULL COMMENT 'API基础URL',
  `api_key` varchar(255) NOT NULL COMMENT 'API密钥',
  `completions_path` varchar(255) NOT NULL COMMENT '补全API路径',
  `embeddings_path` varchar(255) NOT NULL COMMENT '嵌入API路径',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_id` (`api_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAI API配置表';



# 转储表 ai_client_config
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_config`;

CREATE TABLE `ai_client_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_type` varchar(32) NOT NULL COMMENT '源类型（model、client）',
  `source_id` varchar(64) NOT NULL COMMENT '源ID（如 modelId、skillName 等）',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型（model、client）',
  `target_id` varchar(64) NOT NULL COMMENT '目标ID（如 openAiApiId、chatModelId、systemPromptId、advisorId 等）',
  `ext_param` varchar(1024) DEFAULT NULL COMMENT '扩展参数（JSON格式）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_source_id` (`source_id`),
  KEY `idx_target_id` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客户端统一关联配置表';



# 转储表 ai_client_model
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_model`;

CREATE TABLE `ai_client_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
  `model_id` varchar(64) NOT NULL COMMENT '全局唯一模型ID',
  `api_id` varchar(64) NOT NULL COMMENT '关联的API配置ID',
  `model_usage` varchar(128) NOT NULL DEFAULT '缺省的' COMMENT '模型用途',
  `description` varchar(512) NOT NULL DEFAULT '' COMMENT '模型说明',
  `model_name` varchar(64) NOT NULL COMMENT '模型名称',
  `model_type` varchar(32) NOT NULL COMMENT '模型类型：openai、deepseek、claude',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_id` (`model_id`),
  KEY `idx_api_config_id` (`api_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天模型配置表';



# 转储表 ai_client_rag_order
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_rag_order`;

CREATE TABLE `ai_client_rag_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rag_id` varchar(50) NOT NULL COMMENT '知识库ID',
  `rag_name` varchar(50) NOT NULL COMMENT '知识库名称',
  `knowledge_tag` varchar(50) NOT NULL COMMENT '知识标签',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_id` (`rag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库配置表';



# 转储表 ai_client_system_prompt
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_system_prompt`;

CREATE TABLE `ai_client_system_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prompt_id` varchar(64) NOT NULL COMMENT '提示词ID',
  `prompt_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词名称',
  `prompt_content` text NOT NULL COMMENT '提示词内容',
  `description` varchar(1024) DEFAULT NULL COMMENT '描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_id` (`prompt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统提示词配置表';



# 转储表 ai_client_tool_mcp
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_tool_mcp`;

CREATE TABLE `ai_client_tool_mcp` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `mcp_id` varchar(64) NOT NULL COMMENT 'MCP名称',
  `mcp_name` varchar(50) NOT NULL COMMENT 'MCP名称',
  `transport_type` varchar(20) NOT NULL COMMENT '传输类型(sse/stdio)',
  `transport_config` varchar(1024) DEFAULT NULL COMMENT '传输配置(sse/stdio)',
  `request_timeout` int DEFAULT '180' COMMENT '请求超时时间(分钟)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_id` (`mcp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP客户端配置表';




/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;

# 转储表 admin_auth_revoked_token
# ------------------------------------------------------------

DROP TABLE IF EXISTS `admin_auth_revoked_token`;

CREATE TABLE `admin_auth_revoked_token` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `jwt_id` varchar(64) NOT NULL COMMENT 'JWT ID',
  `subject` varchar(128) DEFAULT NULL COMMENT '登录用户名',
  `expires_at` datetime NOT NULL COMMENT '令牌过期时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jwt_id` (`jwt_id`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台JWT撤销表';

# 转储表 ai_ops_agent_definition
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_definition`;

CREATE TABLE `ai_ops_agent_definition` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT 'Agent定义ID',
  `name` varchar(128) DEFAULT NULL COMMENT 'Agent名称',
  `project_id` varchar(80) DEFAULT NULL COMMENT '所属业务系统ID',
  `engine` varchar(64) DEFAULT NULL COMMENT '运行引擎',
  `description` text COMMENT '说明',
  `instruction` mediumtext COMMENT '全局指令',
  `start_node_id` varchar(80) DEFAULT NULL COMMENT '起始节点',
  `definition_json` mediumtext NOT NULL COMMENT 'Agent定义JSON',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `lifecycle` varchar(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期',
  `source` varchar(32) NOT NULL DEFAULT 'YAML' COMMENT '来源',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_engine` (`engine`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent编排定义表';

# 转储表 ai_ops_agent_definition_version
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_definition_version`;

CREATE TABLE `ai_ops_agent_definition_version` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT 'Agent定义ID',
  `version` int NOT NULL COMMENT '版本号',
  `lifecycle` varchar(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期',
  `name` varchar(128) DEFAULT NULL COMMENT 'Agent名称',
  `project_id` varchar(80) DEFAULT NULL COMMENT '所属业务系统ID',
  `engine` varchar(64) DEFAULT NULL COMMENT '运行引擎',
  `description` text COMMENT '说明',
  `instruction` mediumtext COMMENT '全局指令',
  `start_node_id` varchar(80) DEFAULT NULL COMMENT '起始节点',
  `definition_json` mediumtext NOT NULL COMMENT 'Agent定义JSON快照',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '历史版本是否可回放',
  `current_published` tinyint NOT NULL DEFAULT '0' COMMENT '是否当前发布版本',
  `source` varchar(32) NOT NULL DEFAULT 'UI' COMMENT '来源',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version` (`agent_id`,`version`),
  KEY `idx_agent_current` (`agent_id`,`current_published`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent定义历史版本表';

# 转储表 ai_ops_agent_node
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_node`;

CREATE TABLE `ai_ops_agent_node` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT 'Agent定义ID',
  `node_id` varchar(80) NOT NULL COMMENT '节点ID',
  `node_type` varchar(48) NOT NULL COMMENT '节点类型',
  `agent` varchar(128) DEFAULT NULL COMMENT '节点Agent',
  `sub_engine` varchar(64) DEFAULT NULL COMMENT '子引擎',
  `output_key` varchar(80) DEFAULT NULL COMMENT '输出Key',
  `rag_enabled` tinyint DEFAULT NULL COMMENT '是否启用RAG',
  `knowledge_base_id` varchar(128) DEFAULT NULL COMMENT '知识库ID',
  `description` text COMMENT '节点说明',
  `instruction` mediumtext COMMENT '节点指令',
  `config_json` text COMMENT '节点配置JSON',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_node` (`agent_id`,`node_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_node_type` (`node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent节点表';

# 转储表 ai_ops_agent_edge
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_edge`;

CREATE TABLE `ai_ops_agent_edge` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT 'Agent定义ID',
  `from_node_id` varchar(80) NOT NULL COMMENT '起点节点',
  `to_node_id` varchar(80) NOT NULL COMMENT '终点节点',
  `condition_expr` varchar(512) NOT NULL DEFAULT 'always' COMMENT '条件表达式',
  `description` text COMMENT '连线说明',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_from_node` (`agent_id`,`from_node_id`),
  KEY `idx_to_node` (`agent_id`,`to_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent连线表';

# 转储表 ai_ops_agentscope_agent
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agentscope_agent`;

CREATE TABLE `ai_ops_agentscope_agent` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT '所属Agent定义ID',
  `scope_agent_id` varchar(128) DEFAULT NULL COMMENT 'AgentScope子Agent ID',
  `name` varchar(128) DEFAULT NULL COMMENT '名称',
  `instruction` mediumtext COMMENT '指令',
  `output_key` varchar(80) DEFAULT NULL COMMENT '输出Key',
  `rag_enabled` tinyint DEFAULT NULL COMMENT '是否启用RAG',
  `knowledge_base_id` varchar(128) DEFAULT NULL COMMENT '知识库ID',
  `max_iterations` int DEFAULT NULL COMMENT '最大循环次数',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维AgentScope子Agent表';

# 转储表 ai_ops_agent_skill_binding
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_skill_binding`;

CREATE TABLE `ai_ops_agent_skill_binding` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT 'Agent定义ID',
  `owner_type` varchar(32) NOT NULL COMMENT '绑定对象类型',
  `owner_id` varchar(128) NOT NULL COMMENT '绑定对象ID',
  `skill_name` varchar(128) NOT NULL COMMENT 'Skill名称',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_binding` (`agent_id`,`owner_type`,`owner_id`,`skill_name`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_skill_name` (`skill_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent Skill绑定表';

# 转储表 ai_ops_agent_mcp_server
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_mcp_server`;

CREATE TABLE `ai_ops_agent_mcp_server` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `agent_id` varchar(80) NOT NULL COMMENT 'Agent定义ID',
  `owner_type` varchar(32) NOT NULL COMMENT '绑定对象类型',
  `owner_id` varchar(128) NOT NULL COMMENT '绑定对象ID',
  `server_name` varchar(128) NOT NULL COMMENT 'MCP Server名称',
  `description` text COMMENT '说明',
  `transport` varchar(32) NOT NULL DEFAULT 'stdio' COMMENT '传输协议',
  `command_text` text COMMENT 'stdio命令',
  `url` varchar(512) DEFAULT NULL COMMENT 'SSE/HTTP URL',
  `timeout_seconds` int DEFAULT NULL COMMENT '超时秒数',
  `args_json` text COMMENT '命令参数JSON',
  `env_json` text COMMENT '环境变量JSON',
  `headers_json` text COMMENT 'HTTP请求头JSON',
  `tool_capabilities_json` text COMMENT '工具能力JSON',
  `allowed_tools_json` text COMMENT '只读允许工具JSON',
  `notification_tools_json` text COMMENT '通知工具JSON',
  `blocked_tools_json` text COMMENT '禁用工具JSON',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_owner` (`agent_id`,`owner_type`,`owner_id`),
  KEY `idx_transport` (`transport`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent MCP绑定表';

# 转储表 ai_ops_project
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_project`;

CREATE TABLE `ai_ops_project` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `project_id` varchar(80) NOT NULL COMMENT '业务系统ID',
  `name` varchar(128) NOT NULL COMMENT '业务系统名称',
  `description` text COMMENT '说明',
  `owner` varchar(80) DEFAULT NULL COMMENT '负责人',
  `environments_json` text COMMENT '环境列表JSON',
  `knowledge_base_id` varchar(128) DEFAULT NULL COMMENT '默认知识库ID',
  `default_agent_id` varchar(128) NOT NULL DEFAULT 'generic-ops-react-agent' COMMENT '项目默认Agent',
  `skill_ids_json` text COMMENT '项目允许使用的Skill ID列表',
  `shared_mcp_ids_json` text COMMENT '项目绑定的通用MCP ID列表',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_id` (`project_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维业务系统空间表';

# 转储表 ai_ops_project_resource
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_project_resource`;

CREATE TABLE `ai_ops_project_resource` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `resource_id` varchar(128) NOT NULL COMMENT '资源ID',
  `project_id` varchar(80) NOT NULL COMMENT '业务系统ID',
  `resource_type` varchar(48) NOT NULL COMMENT '资源类型',
  `type_name` varchar(80) DEFAULT NULL COMMENT '资源类型名称',
  `name` varchar(128) NOT NULL COMMENT '资源名称',
  `environment` varchar(32) NOT NULL DEFAULT 'prod' COMMENT '环境',
  `endpoint` varchar(512) NOT NULL COMMENT '连接地址',
  `credential_json` text COMMENT '凭据JSON',
  `status` varchar(32) NOT NULL DEFAULT 'PREVIEW' COMMENT '扫描状态',
  `schema_json` mediumtext COMMENT '对象结构JSON',
  `permission_json` mediumtext COMMENT '权限策略JSON',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_id` (`resource_id`),
  KEY `idx_project_type` (`project_id`,`resource_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维业务系统资源表';

# 转储表 ai_ops_project_mcp
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_project_mcp`;

CREATE TABLE `ai_ops_project_mcp` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `mcp_id` varchar(128) NOT NULL COMMENT '项目级MCP ID',
  `mcp_name` varchar(160) NOT NULL COMMENT 'MCP名称',
  `project_id` varchar(80) NOT NULL COMMENT '业务系统ID',
  `resource_id` varchar(128) NOT NULL COMMENT '资源ID',
  `resource_type` varchar(48) NOT NULL COMMENT '资源类型',
  `transport_type` varchar(32) NOT NULL DEFAULT 'stdio' COMMENT '传输类型',
  `transport_config_json` mediumtext COMMENT '脱敏传输配置JSON',
  `request_timeout` int NOT NULL DEFAULT '30' COMMENT '超时秒',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_id` (`mcp_id`),
  KEY `idx_project_resource` (`project_id`,`resource_id`),
  KEY `idx_resource_type` (`resource_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维项目级MCP表';

# 转储表 ai_ops_chat_session
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_chat_session`;

CREATE TABLE `ai_ops_chat_session` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `session_id` varchar(80) NOT NULL COMMENT '会话ID',
  `user_id` varchar(80) DEFAULT NULL COMMENT '用户ID',
  `project_id` varchar(80) DEFAULT NULL COMMENT '业务系统ID',
  `agent_id` varchar(128) DEFAULT NULL COMMENT 'Agent定义ID',
  `agent_version` int DEFAULT NULL COMMENT 'Agent定义版本',
  `title` varchar(180) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
  `mode` varchar(32) NOT NULL DEFAULT 'MULTI_TURN' COMMENT '会话模式',
  `engine` varchar(32) NOT NULL DEFAULT 'CHAT' COMMENT '运行引擎',
  `rag_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用RAG',
  `knowledge_base_id` varchar(128) DEFAULT NULL COMMENT '知识库ID',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `metadata` text COMMENT '扩展信息',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_active_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_agent` (`user_id`,`agent_id`),
  KEY `idx_last_active` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用Agent会话表';

# 转储表 ai_ops_chat_message
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_chat_message`;

CREATE TABLE `ai_ops_chat_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `session_id` varchar(80) NOT NULL COMMENT '会话ID',
  `user_id` varchar(80) DEFAULT NULL COMMENT '用户ID',
  `role` varchar(32) NOT NULL COMMENT '消息角色',
  `content` mediumtext NOT NULL COMMENT '消息内容',
  `metadata` text COMMENT '扩展信息',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent对话消息表';

# 转储表 ai_ops_memory_item
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_memory_item`;

CREATE TABLE `ai_ops_memory_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `session_id` varchar(80) NOT NULL COMMENT '会话ID',
  `user_id` varchar(80) DEFAULT NULL COMMENT '用户ID',
  `memory_type` varchar(32) NOT NULL COMMENT '记忆类型',
  `content` mediumtext NOT NULL COMMENT '记忆内容',
  `importance` decimal(5,4) NOT NULL DEFAULT '0.5000' COMMENT '重要度',
  `tags_json` text COMMENT '标签JSON',
  `source_message_role` varchar(32) DEFAULT NULL COMMENT '来源消息角色',
  `source_message_hash` varchar(64) DEFAULT NULL COMMENT '来源消息哈希',
  `metadata` text COMMENT '扩展信息',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_type` (`session_id`,`memory_type`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_importance` (`importance`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent长期记忆条目表';

# 转储表 ai_ops_agent_run
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_run`;

CREATE TABLE `ai_ops_agent_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `run_id` varchar(80) NOT NULL COMMENT '异步分析任务ID',
  `status` varchar(32) NOT NULL COMMENT '任务状态',
  `request_json` mediumtext COMMENT '请求JSON',
  `response_json` mediumtext COMMENT '响应JSON',
  `error_message` text COMMENT '错误信息',
  `created_at` varchar(32) NOT NULL COMMENT '创建时间',
  `updated_at` varchar(32) NOT NULL COMMENT '更新时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例运维AI异步分析任务表';

# 转储表 ai_ops_agent_audit
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_audit`;

CREATE TABLE `ai_ops_agent_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `analysis_id` varchar(64) NOT NULL COMMENT '分析ID',
  `success` tinyint NOT NULL COMMENT '是否成功',
  `question` text COMMENT '用户问题',
  `intent` varchar(64) DEFAULT NULL COMMENT '主Agent意图',
  `range_minutes` int DEFAULT NULL COMMENT '日志时间窗口',
  `prom_window` varchar(16) DEFAULT NULL COMMENT 'Prometheus rate窗口',
  `generated_at` varchar(32) DEFAULT NULL COMMENT '分析生成时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `selected_sources_json` text COMMENT '计划选择数据源',
  `executed_sources_json` text COMMENT '实际执行数据源',
  `skipped_sources_json` text COMMENT '跳过数据源',
  `result_statuses_json` text COMMENT '子Agent结果状态',
  `insight_levels_json` text COMMENT '规则洞察等级',
  `conclusion` text COMMENT '首要结论',
  `error_message` text COMMENT '失败原因',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_id` (`analysis_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例运维AI分析审计表';

# 转储表 ai_ops_agent_node_trace
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_agent_node_trace`;

CREATE TABLE `ai_ops_agent_node_trace` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `run_id` varchar(80) DEFAULT NULL COMMENT '异步分析任务ID',
  `analysis_id` varchar(80) DEFAULT NULL COMMENT '分析ID',
  `sequence_no` bigint NOT NULL COMMENT '事件序号',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型',
  `node_id` varchar(80) DEFAULT NULL COMMENT 'Graph节点ID',
  `node_type` varchar(48) DEFAULT NULL COMMENT 'Graph节点类型',
  `agent` varchar(80) DEFAULT NULL COMMENT 'Agent',
  `source` varchar(80) DEFAULT NULL COMMENT '数据源',
  `status` varchar(32) DEFAULT NULL COMMENT '节点状态',
  `summary` text COMMENT '节点摘要',
  `started_at` varchar(32) DEFAULT NULL COMMENT '开始时间',
  `finished_at` varchar(32) DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `payload_json` text COMMENT '扩展上下文',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_analysis_id` (`analysis_id`),
  KEY `idx_node_id` (`node_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent Graph节点事件与审计表';

# 转储表 ai_ops_alert_trigger_rule
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_alert_trigger_rule`;

CREATE TABLE `ai_ops_alert_trigger_rule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 0禁用 1启用',
  `source_type` varchar(32) NOT NULL DEFAULT 'ALERTMANAGER' COMMENT '来源类型',
  `alert_name_regex` varchar(255) DEFAULT NULL COMMENT 'alertname 正则',
  `severity_regex` varchar(255) DEFAULT NULL COMMENT 'severity 正则',
  `service_regex` varchar(255) DEFAULT NULL COMMENT 'service/app/job 正则',
  `match_labels_json` text COMMENT '标签匹配 JSON，值以 ~ 开头表示正则',
  `receiver` varchar(160) DEFAULT NULL COMMENT 'Weixin 接收人',
  `webhook_secret` varchar(160) DEFAULT NULL COMMENT 'Webhook签名密钥',
  `project_id` varchar(80) DEFAULT NULL COMMENT '业务系统ID',
  `agent_definition_id` varchar(128) DEFAULT NULL COMMENT 'Agent 定义 ID',
  `question_template` text COMMENT '问题模板',
  `range_minutes` int NOT NULL DEFAULT '15' COMMENT '分析窗口',
  `prom_window` varchar(16) NOT NULL DEFAULT '5m' COMMENT 'Prometheus 窗口',
  `include_recent_logs` tinyint NOT NULL DEFAULT '1' COMMENT '是否查最近日志',
  `notify_weixin` tinyint NOT NULL DEFAULT '1' COMMENT '是否微信推送',
  `sub_agent_max_iterations` int DEFAULT NULL COMMENT '子 Agent 循环上限',
  `node_timeout_seconds` int DEFAULT NULL COMMENT '节点超时秒',
  `max_evidence_items` int DEFAULT NULL COMMENT '证据条数上限',
  `dedup_window_seconds` int NOT NULL DEFAULT '900' COMMENT '去重窗口秒',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_source` (`status`,`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发规则表';

# 转储表 ai_ops_alert_trigger_event
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_alert_trigger_event`;

CREATE TABLE `ai_ops_alert_trigger_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint DEFAULT NULL COMMENT '规则ID',
  `rule_name` varchar(128) DEFAULT NULL COMMENT '规则名称',
  `source_type` varchar(32) NOT NULL COMMENT '来源类型',
  `status` varchar(32) NOT NULL COMMENT '处理状态',
  `dedup_key` varchar(128) DEFAULT NULL COMMENT '幂等去重键',
  `fingerprint` varchar(160) NOT NULL COMMENT '告警指纹',
  `alert_name` varchar(160) DEFAULT NULL COMMENT '告警名',
  `severity` varchar(64) DEFAULT NULL COMMENT '级别',
  `service_name` varchar(160) DEFAULT NULL COMMENT '服务名',
  `receiver` varchar(160) DEFAULT NULL COMMENT '接收人',
  `run_id` varchar(80) DEFAULT NULL COMMENT '异步分析任务ID',
  `run_status` varchar(32) DEFAULT NULL COMMENT '异步分析最终状态',
  `final_summary` text COMMENT '最终摘要',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  `error_message` text COMMENT '错误信息',
  `labels_json` text COMMENT '告警标签',
  `annotations_json` text COMMENT '告警注解',
  `payload_json` mediumtext COMMENT '原始告警',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_fingerprint_time` (`rule_id`,`fingerprint`,`create_time`),
  KEY `idx_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发事件表';

# 转储表 ai_ops_alert_trigger_dedup
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_alert_trigger_dedup`;

CREATE TABLE `ai_ops_alert_trigger_dedup` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dedup_key` varchar(128) NOT NULL COMMENT '幂等去重键',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `fingerprint` varchar(160) NOT NULL COMMENT '告警指纹',
  `acquired_token` varchar(64) NOT NULL COMMENT '本轮获取令牌',
  `run_id` varchar(80) DEFAULT NULL COMMENT '异步分析任务ID',
  `window_until` timestamp NOT NULL COMMENT '去重窗口截止时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_rule_fingerprint` (`rule_id`,`fingerprint`),
  KEY `idx_window_until` (`window_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发幂等表';

# 转储表 ai_ops_alert_trigger_outbox
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_ops_alert_trigger_outbox`;

CREATE TABLE `ai_ops_alert_trigger_outbox` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dedup_key` varchar(128) NOT NULL COMMENT '幂等去重键',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `fingerprint` varchar(160) NOT NULL COMMENT '告警指纹',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/RUNNING/SUCCEEDED/FAILED',
  `run_id` varchar(80) DEFAULT NULL COMMENT '异步分析任务ID',
  `request_json` mediumtext NOT NULL COMMENT '待提交分析请求',
  `payload_json` mediumtext COMMENT '原始告警',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `next_retry_at` timestamp NULL DEFAULT NULL COMMENT '下次重试时间',
  `locked_token` varchar(64) DEFAULT NULL COMMENT '处理锁令牌',
  `error_message` text COMMENT '错误信息',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup_key` (`dedup_key`),
  KEY `idx_status_retry` (`status`,`next_retry_at`),
  KEY `idx_rule_fingerprint` (`rule_id`,`fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维告警触发 Outbox 表';

# 转储表 ai_rag_ingestion_job
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_rag_ingestion_job`;

CREATE TABLE `ai_rag_ingestion_job` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `job_id` varchar(80) NOT NULL COMMENT 'RAG入库任务ID',
  `status` varchar(32) NOT NULL COMMENT '任务状态',
  `name` varchar(128) NOT NULL COMMENT '知识库名称',
  `tag` varchar(128) NOT NULL COMMENT '知识标签',
  `file_names_json` text COMMENT '文件名列表',
  `total_bytes` bigint DEFAULT NULL COMMENT '文件总大小',
  `error_message` text COMMENT '失败原因',
  `created_at` varchar(32) NOT NULL COMMENT '创建时间',
  `updated_at` varchar(32) NOT NULL COMMENT '更新时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG异步入库任务表';
