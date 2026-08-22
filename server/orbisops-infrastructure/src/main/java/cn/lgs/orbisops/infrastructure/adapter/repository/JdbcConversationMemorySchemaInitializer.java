package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Dev/test compatibility schema owner for conversation messages and cold memory items. */
@Component
public class JdbcConversationMemorySchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.chat.memory.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcConversationMemorySchemaInitializer(
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
            createMessageTable(template);
            createMemoryItemTable(template);
            initialized = true;
        }
    }

    private void createMessageTable(JdbcTemplate template) {
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_chat_message (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  session_id VARCHAR(80) NOT NULL COMMENT '会话ID',
                  user_id VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
                  role VARCHAR(32) NOT NULL COMMENT '消息角色',
                  content MEDIUMTEXT NOT NULL COMMENT '消息内容',
                  metadata TEXT COMMENT '扩展信息',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_session_id (session_id),
                  KEY idx_user_id (user_id),
                  KEY idx_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent对话消息表'
                """);
    }

    private void createMemoryItemTable(JdbcTemplate template) {
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_memory_item (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  session_id VARCHAR(80) NOT NULL COMMENT '会话ID',
                  user_id VARCHAR(80) DEFAULT NULL COMMENT '用户ID',
                  memory_type VARCHAR(32) NOT NULL COMMENT '记忆类型：fact/summary/preference/task',
                  content MEDIUMTEXT NOT NULL COMMENT '记忆内容',
                  importance DECIMAL(5,4) NOT NULL DEFAULT 0.5000 COMMENT '重要度',
                  tags_json TEXT COMMENT '标签JSON',
                  source_message_role VARCHAR(32) DEFAULT NULL COMMENT '来源消息角色',
                  source_message_hash VARCHAR(64) DEFAULT NULL COMMENT '来源消息哈希',
                  metadata TEXT COMMENT '扩展信息',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_session_type (session_id, memory_type),
                  KEY idx_user_id (user_id),
                  KEY idx_importance (importance),
                  KEY idx_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent长期记忆条目表'
                """);
    }
}
