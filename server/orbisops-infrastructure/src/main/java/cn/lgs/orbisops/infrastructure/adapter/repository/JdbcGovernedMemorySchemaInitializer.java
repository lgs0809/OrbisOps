package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Dev/test compatibility schema owner for governed explicit memories. */
@Slf4j
@Component
public class JdbcGovernedMemorySchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.memory.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcGovernedMemorySchemaInitializer(
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
                        CREATE TABLE IF NOT EXISTS ai_ops_memory (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          memory_id VARCHAR(80) NOT NULL,
                          scope_type VARCHAR(24) NOT NULL,
                          scope_id VARCHAR(128) NOT NULL,
                          user_id VARCHAR(128) NOT NULL DEFAULT '',
                          project_id VARCHAR(128) NOT NULL DEFAULT '',
                          agent_id VARCHAR(128) NOT NULL DEFAULT '',
                          session_id VARCHAR(100) NOT NULL DEFAULT '',
                          memory_type VARCHAR(64) NOT NULL,
                          logical_key VARCHAR(256) NOT NULL,
                          content MEDIUMTEXT NOT NULL,
                          normalized_content MEDIUMTEXT NOT NULL,
                          source_type VARCHAR(32) NOT NULL DEFAULT 'USER_ASSERTED',
                          source_run_id VARCHAR(100) NOT NULL DEFAULT '',
                          verified TINYINT NOT NULL DEFAULT 0,
                          confidence DECIMAL(5,4) NOT NULL DEFAULT 0.6000,
                          risk_level VARCHAR(24) NOT NULL DEFAULT 'LOW',
                          status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                          version INT NOT NULL DEFAULT 1,
                          memory_hash VARCHAR(64) NOT NULL,
                          proof_refs_json MEDIUMTEXT NULL,
                          expires_at TIMESTAMP NULL DEFAULT NULL,
                          created_by VARCHAR(128) NOT NULL DEFAULT '',
                          idempotency_key VARCHAR(64) NOT NULL,
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_memory_id (memory_id),
                          UNIQUE KEY uk_memory_idempotency (idempotency_key),
                          KEY idx_memory_scope (scope_type,scope_id,status,update_time),
                          KEY idx_memory_project (project_id,update_time),
                          KEY idx_memory_run (source_run_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='显式Memory及版本指针'
                        """);
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_memory_version (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          memory_id VARCHAR(80) NOT NULL,
                          version INT NOT NULL,
                          memory_hash VARCHAR(64) NOT NULL,
                          status VARCHAR(32) NOT NULL,
                          content MEDIUMTEXT NOT NULL,
                          normalized_content MEDIUMTEXT NOT NULL,
                          source_run_id VARCHAR(100) NOT NULL DEFAULT '',
                          created_by VARCHAR(128) NOT NULL DEFAULT '',
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_memory_version (memory_id,version),
                          KEY idx_memory_version_hash (memory_hash)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Memory append-only版本'
                        """);
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_memory_conflict (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          conflict_id VARCHAR(80) NOT NULL,
                          scope_type VARCHAR(24) NOT NULL,
                          scope_id VARCHAR(128) NOT NULL,
                          logical_key VARCHAR(256) NOT NULL,
                          existing_memory_id VARCHAR(80) NOT NULL,
                          incoming_memory_id VARCHAR(80) NOT NULL,
                          status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                          resolution_json MEDIUMTEXT NULL,
                          created_by VARCHAR(128) NOT NULL DEFAULT '',
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          resolved_at TIMESTAMP NULL DEFAULT NULL,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_memory_conflict_id (conflict_id),
                          KEY idx_memory_conflict_scope (scope_type,scope_id,status)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='显式Memory冲突记录'
                        """);
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_memory_audit (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          audit_id VARCHAR(80) NOT NULL,
                          memory_id VARCHAR(80) NOT NULL,
                          action VARCHAR(64) NOT NULL,
                          actor VARCHAR(128) NOT NULL DEFAULT '',
                          payload_json MEDIUMTEXT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_memory_audit_id (audit_id),
                          KEY idx_memory_audit_memory (memory_id,created_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Memory治理审计'
                        """);
                initialized = true;
            } catch (DataAccessException error) {
                log.warn("Governed Memory 表初始化失败：{}", error.getMessage());
            }
        }
    }
}
