package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Dev/test compatibility schema owner for user/project Context Memory. */
@Slf4j
@Component
public class JdbcContextMemorySchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.context-memory.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcContextMemorySchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void init() {
        initialize();
    }

    public void initialize() {
        if (initialized || !autoInit) return;
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) return;
        synchronized (this) {
            if (initialized || !autoInit) return;
            try {
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_context_memory (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          memory_id VARCHAR(80) NOT NULL,
                          scope_type VARCHAR(24) NOT NULL COMMENT 'USER/PROJECT',
                          scope_id VARCHAR(128) NOT NULL COMMENT 'userId/projectId',
                          memory_type VARCHAR(64) NOT NULL,
                          title VARCHAR(256) NOT NULL,
                          summary VARCHAR(1000) NOT NULL,
                          content MEDIUMTEXT NOT NULL,
                          keywords VARCHAR(1000) DEFAULT '',
                          status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                          confidence DECIMAL(5,4) NOT NULL DEFAULT 0.8000,
                          source_type VARCHAR(32) DEFAULT '',
                          source_id VARCHAR(128) DEFAULT '',
                          source_message_hash VARCHAR(64) DEFAULT '',
                          created_by VARCHAR(128) DEFAULT '',
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          expire_time TIMESTAMP NULL DEFAULT NULL,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_memory_id (memory_id),
                          KEY idx_scope_type_id (scope_type, scope_id),
                          KEY idx_scope_type_memory (scope_type, scope_id, memory_type),
                          KEY idx_status_update (status, update_time)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户和项目长期上下文记忆'
                        """);
                initialized = true;
            } catch (DataAccessException error) {
                log.warn("Context Memory 表初始化失败：{}", error.getMessage());
            }
        }
    }
}
