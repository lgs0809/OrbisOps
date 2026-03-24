package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Infrastructure-only compatibility schema owner for Agent Definition persistence. */
@Slf4j
@Component
public final class JdbcAgentDefinitionSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.agents.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcAgentDefinitionSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        if (initialized || !autoInit || jdbcTemplate == null) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try {
                createDefinitionTables();
                createGraphTables();
                createBindingTables();
                initialized = true;
            } catch (DataAccessException error) {
                log.warn("Agent Definition 表初始化失败，DB Definition 能力降级：{}", error.getMessage());
            }
        }
    }

    private void createDefinitionTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_definition (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  name VARCHAR(128) NULL COMMENT 'Agent名称',
                  project_id VARCHAR(80) NULL COMMENT '所属业务系统ID',
                  engine VARCHAR(64) NULL COMMENT '运行引擎',
                  description TEXT NULL COMMENT '说明',
                  instruction MEDIUMTEXT NULL COMMENT '全局指令',
                  start_node_id VARCHAR(80) NULL COMMENT '起始节点',
                  definition_json MEDIUMTEXT NOT NULL COMMENT 'Agent定义JSON',
                  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                  version INT NOT NULL DEFAULT 1 COMMENT '版本号',
                  definition_hash VARCHAR(64) NOT NULL DEFAULT '' COMMENT '定义 canonical SHA-256',
                  lifecycle VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期',
                  source VARCHAR(32) NOT NULL DEFAULT 'YAML' COMMENT '来源',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_agent_id (agent_id),
                  KEY idx_enabled (enabled),
                  KEY idx_engine (engine),
                  KEY idx_update_time (update_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent编排定义表'
                """);
        ensureColumn("ai_ops_agent_definition", "name", "VARCHAR(128) NULL COMMENT 'Agent名称'");
        ensureColumn("ai_ops_agent_definition", "project_id", "VARCHAR(80) NULL COMMENT '所属业务系统ID'");
        ensureColumn("ai_ops_agent_definition", "engine", "VARCHAR(64) NULL COMMENT '运行引擎'");
        ensureColumn("ai_ops_agent_definition", "description", "TEXT NULL COMMENT '说明'");
        ensureColumn("ai_ops_agent_definition", "instruction", "MEDIUMTEXT NULL COMMENT '全局指令'");
        ensureColumn("ai_ops_agent_definition", "start_node_id", "VARCHAR(80) NULL COMMENT '起始节点'");
        ensureColumn("ai_ops_agent_definition", "version", "INT NOT NULL DEFAULT 1 COMMENT '版本号'");
        ensureColumn("ai_ops_agent_definition", "definition_hash", "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '定义 canonical SHA-256'");
        ensureColumn("ai_ops_agent_definition", "lifecycle", "VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期'");
        ensureColumn("ai_ops_agent_definition", "source", "VARCHAR(32) NOT NULL DEFAULT 'YAML' COMMENT '来源'");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_definition_version (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  version INT NOT NULL COMMENT '版本号',
                  definition_hash VARCHAR(64) NOT NULL DEFAULT '' COMMENT '定义 canonical SHA-256',
                  lifecycle VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本生命周期',
                  name VARCHAR(128) NULL COMMENT 'Agent名称',
                  project_id VARCHAR(80) NULL COMMENT '所属业务系统ID',
                  engine VARCHAR(64) NULL COMMENT '运行引擎',
                  description TEXT NULL COMMENT '说明',
                  instruction MEDIUMTEXT NULL COMMENT '全局指令',
                  start_node_id VARCHAR(80) NULL COMMENT '起始节点',
                  definition_json MEDIUMTEXT NOT NULL COMMENT 'Agent定义JSON快照',
                  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '历史版本是否可回放',
                  current_published TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前发布版本',
                  source VARCHAR(32) NOT NULL DEFAULT 'UI' COMMENT '来源',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_agent_version (agent_id, version),
                  KEY idx_agent_current (agent_id, current_published),
                  KEY idx_enabled (enabled),
                  KEY idx_update_time (update_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent定义历史版本表'
                """);
        ensureColumn("ai_ops_agent_definition_version", "definition_hash", "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '定义 canonical SHA-256'");
        ensureColumn("ai_ops_agent_definition_version", "project_id", "VARCHAR(80) NULL COMMENT '所属业务系统ID'");
        ensureColumn("ai_ops_agent_definition_version", "source", "VARCHAR(32) NOT NULL DEFAULT 'UI' COMMENT '来源'");
    }

    private void createGraphTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_node (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  node_id VARCHAR(80) NOT NULL COMMENT '节点ID',
                  node_type VARCHAR(48) NOT NULL COMMENT '节点类型',
                  agent VARCHAR(128) NULL COMMENT '节点Agent',
                  sub_engine VARCHAR(64) NULL COMMENT '子引擎',
                  output_key VARCHAR(80) NULL COMMENT '输出Key',
                  rag_enabled TINYINT NULL COMMENT '是否启用RAG',
                  knowledge_base_id VARCHAR(128) NULL COMMENT '知识库ID',
                  description TEXT NULL COMMENT '节点说明',
                  instruction MEDIUMTEXT NULL COMMENT '节点指令',
                  config_json TEXT NULL COMMENT '节点配置JSON',
                  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_agent_node (agent_id, node_id),
                  KEY idx_agent_id (agent_id),
                  KEY idx_node_type (node_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent节点表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_edge (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  edge_id VARCHAR(128) NULL COMMENT '连线ID',
                  edge_name VARCHAR(128) NULL COMMENT '连线名称',
                  from_node_id VARCHAR(80) NOT NULL COMMENT '起点节点',
                  to_node_id VARCHAR(80) NOT NULL COMMENT '终点节点',
                  condition_type VARCHAR(48) NOT NULL DEFAULT 'always' COMMENT '条件类型',
                  condition_expr TEXT NOT NULL DEFAULT ('always') COMMENT '条件表达式或有界规则AST',
                  default_edge TINYINT NULL COMMENT '是否默认边',
                  feedback_edge TINYINT NULL COMMENT '是否回边',
                  priority_order INT NULL COMMENT '条件优先级',
                  data_mapping_json TEXT NULL COMMENT '数据映射JSON',
                  description TEXT NULL COMMENT '连线说明',
                  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_agent_id (agent_id),
                  KEY idx_from_node (agent_id, from_node_id),
                  KEY idx_to_node (agent_id, to_node_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent连线表'
                """);
        ensureColumn("ai_ops_agent_edge", "edge_id", "VARCHAR(128) NULL COMMENT '连线ID'");
        ensureColumn("ai_ops_agent_edge", "edge_name", "VARCHAR(128) NULL COMMENT '连线名称'");
        ensureColumn("ai_ops_agent_edge", "condition_type", "VARCHAR(48) NOT NULL DEFAULT 'always' COMMENT '条件类型'");
        ensureColumn("ai_ops_agent_edge", "default_edge", "TINYINT NULL COMMENT '是否默认边'");
        ensureColumn("ai_ops_agent_edge", "feedback_edge", "TINYINT NULL COMMENT '是否回边'");
        ensureColumn("ai_ops_agent_edge", "priority_order", "INT NULL COMMENT '条件优先级'");
        ensureColumn("ai_ops_agent_edge", "data_mapping_json", "TEXT NULL COMMENT '数据映射JSON'");
        String ruleType = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_agent_edge'
                  AND COLUMN_NAME='condition_expr'
                """, String.class);
        if ("varchar".equalsIgnoreCase(ruleType) || "char".equalsIgnoreCase(ruleType)) {
            jdbcTemplate.execute("""
                    ALTER TABLE ai_ops_agent_edge MODIFY condition_expr
                    TEXT NOT NULL DEFAULT ('always') COMMENT '条件表达式或有界规则AST'
                    """);
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agentscope_agent (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT '所属Agent定义ID',
                  scope_agent_id VARCHAR(128) NULL COMMENT 'AgentScope子Agent ID',
                  name VARCHAR(128) NULL COMMENT '名称',
                  instruction MEDIUMTEXT NULL COMMENT '指令',
                  output_key VARCHAR(80) NULL COMMENT '输出Key',
                  rag_enabled TINYINT NULL COMMENT '是否启用RAG',
                  knowledge_base_id VARCHAR(128) NULL COMMENT '知识库ID',
                  max_iterations INT NULL COMMENT '最大循环次数',
                  max_depth INT NOT NULL DEFAULT 1 COMMENT '子Agent最大派生深度',
                  role VARCHAR(64) NULL COMMENT '内置窄职责角色',
                  allowed_tools_json TEXT NULL COMMENT '运行时工具白名单',
                  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_agent_id (agent_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维AgentScope子Agent表'
                """);
        ensureColumn("ai_ops_agentscope_agent", "max_depth", "INT NOT NULL DEFAULT 1 COMMENT '子Agent最大派生深度'");
        ensureColumn("ai_ops_agentscope_agent", "role", "VARCHAR(64) NULL COMMENT '内置窄职责角色'");
        ensureColumn("ai_ops_agentscope_agent", "allowed_tools_json", "TEXT NULL COMMENT '运行时工具白名单'");
    }

    private void createBindingTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_skill_binding (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  owner_type VARCHAR(32) NOT NULL COMMENT '绑定对象类型：AGENT/NODE/AGENTSCOPE',
                  owner_id VARCHAR(128) NOT NULL COMMENT '绑定对象ID',
                  skill_name VARCHAR(128) NOT NULL COMMENT 'Skill名称',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_binding (agent_id, owner_type, owner_id, skill_name),
                  KEY idx_agent_id (agent_id),
                  KEY idx_skill_name (skill_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent Skill绑定表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_mcp_server (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  owner_type VARCHAR(32) NOT NULL COMMENT '绑定对象类型：AGENT/NODE/AGENTSCOPE',
                  owner_id VARCHAR(128) NOT NULL COMMENT '绑定对象ID',
                  server_name VARCHAR(128) NOT NULL COMMENT 'MCP Server名称',
                  description TEXT NULL COMMENT '说明',
                  transport VARCHAR(32) NOT NULL DEFAULT 'stdio' COMMENT '传输协议',
                  command_text TEXT NULL COMMENT 'stdio命令',
                  url VARCHAR(512) NULL COMMENT 'SSE/HTTP URL',
                  timeout_seconds INT NULL COMMENT '超时秒数',
                  args_json TEXT NULL COMMENT '命令参数JSON',
                  env_json TEXT NULL COMMENT '环境变量JSON',
                  headers_json TEXT NULL COMMENT 'HTTP请求头JSON',
                  tool_capabilities_json TEXT NULL COMMENT '工具能力JSON',
                  allowed_tools_json TEXT NULL COMMENT '只读允许工具JSON',
                  notification_tools_json TEXT NULL COMMENT '通知工具JSON',
                  blocked_tools_json TEXT NULL COMMENT '禁用工具JSON',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_agent_id (agent_id),
                  KEY idx_owner (agent_id, owner_type, owner_id),
                  KEY idx_transport (transport)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent MCP绑定表'
                """);
        ensureColumn("ai_ops_agent_mcp_server", "tool_capabilities_json", "TEXT NULL COMMENT '工具能力JSON'");
        ensureColumn("ai_ops_agent_mcp_server", "allowed_tools_json", "TEXT NULL COMMENT '只读允许工具JSON'");
        ensureColumn("ai_ops_agent_mcp_server", "notification_tools_json", "TEXT NULL COMMENT '通知工具JSON'");
        ensureColumn("ai_ops_agent_mcp_server", "blocked_tools_json", "TEXT NULL COMMENT '禁用工具JSON'");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_agent_capability_binding (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  agent_id VARCHAR(80) NOT NULL COMMENT 'Agent定义ID',
                  version INT NOT NULL DEFAULT 0 COMMENT 'Agent版本',
                  lifecycle VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '版本生命周期',
                  project_id VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目ID',
                  owner_type VARCHAR(32) NOT NULL COMMENT '绑定对象类型：AGENT/NODE/AGENTSCOPE',
                  node_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '节点或子Agent ID，Agent级为空',
                  capability_type VARCHAR(48) NOT NULL COMMENT '能力类型：skill/project_tool/knowledge_base/inline_mcp_server',
                  capability_id VARCHAR(256) NOT NULL COMMENT '能力ID',
                  capability_scope VARCHAR(48) NOT NULL DEFAULT 'PROJECT' COMMENT '能力范围',
                  bind_config_json TEXT NULL COMMENT '绑定配置JSON',
                  create_by VARCHAR(80) NULL COMMENT '创建人',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_agent_capability (agent_id, version, owner_type, node_id, capability_type, capability_id),
                  KEY idx_agent_version (agent_id, version),
                  KEY idx_project_type (project_id, capability_type),
                  KEY idx_capability (capability_type, capability_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent统一能力绑定表'
                """);
    }

    private void ensureColumn(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}
