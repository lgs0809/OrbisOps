package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Dev/test compatibility schema owner for Chat Session persistence. Production migrations remain authoritative. */
@Component
public class JdbcChatSessionSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.chat.session.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcChatSessionSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public void initialize() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
            if (template == null || !autoInit) {
                initialized = true;
                return;
            }
            createSessionTable(template);
            createParticipantTable(template);
            initialized = true;
        }
    }

    private void createSessionTable(JdbcTemplate template) {
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_chat_session (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  session_id VARCHAR(80) NOT NULL COMMENT '会话ID',
                  user_id VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
                  project_id VARCHAR(80) DEFAULT NULL COMMENT '业务系统ID',
                  agent_id VARCHAR(128) DEFAULT NULL COMMENT 'Agent定义ID',
                  agent_binding_mode VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED' COMMENT 'Agent版本绑定模式',
                  agent_version INT NULL COMMENT 'Agent定义版本',
                  agent_definition_hash VARCHAR(64) NOT NULL DEFAULT '' COMMENT '最近解析的Agent定义哈希',
                  title VARCHAR(180) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
                  mode VARCHAR(32) NOT NULL DEFAULT 'MULTI_TURN' COMMENT '会话模式',
                  engine VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT '运行引擎',
                  rag_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用RAG',
                  knowledge_base_id VARCHAR(128) DEFAULT NULL COMMENT '知识库ID',
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
                  state_version BIGINT NOT NULL DEFAULT 1 COMMENT '会话CAS版本',
                  metadata TEXT COMMENT '扩展信息',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_session_id (session_id),
                  KEY idx_user_agent (user_id, agent_id),
                  KEY idx_last_active (last_active_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用Agent会话表'
                """);
        addColumnIfMissing(template, "ai_ops_chat_session", "agent_version",
                "INT NULL COMMENT 'Agent定义版本'");
        addColumnIfMissing(template, "ai_ops_chat_session", "agent_binding_mode",
                "VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED' COMMENT 'Agent版本绑定模式'");
        addColumnIfMissing(template, "ai_ops_chat_session", "agent_definition_hash",
                "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '最近解析的Agent定义哈希'");
        addColumnIfMissing(template, "ai_ops_chat_session", "project_id",
                "VARCHAR(80) NULL COMMENT '业务系统ID'");
        addColumnIfMissing(template, "ai_ops_chat_session", "state_version",
                "BIGINT NOT NULL DEFAULT 1 COMMENT '会话CAS版本'");
    }

    private void createParticipantTable(JdbcTemplate template) {
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_chat_session_participant (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  session_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(80) NOT NULL DEFAULT '',
                  participant_user_id VARCHAR(80) NOT NULL,
                  participant_role VARCHAR(24) NOT NULL DEFAULT 'OBSERVER',
                  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                  state_version BIGINT NOT NULL DEFAULT 1,
                  added_by VARCHAR(80) NOT NULL DEFAULT '',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_session_participant (session_id, participant_user_id),
                  KEY idx_participant_user (participant_user_id, status),
                  KEY idx_participant_project (project_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat Session owner and participants'
                """);
    }

    private void addColumnIfMissing(JdbcTemplate template,
                                    String tableName,
                                    String columnName,
                                    String definition) {
        Integer count = template.queryForObject("""
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
            template.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}
