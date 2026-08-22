package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.security.JwtRevocationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;

@Slf4j
@Repository
public class JwtRevocationRepository implements JwtRevocationPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.admin.auth.revocation.jdbc-enabled:true}")
    private boolean jdbcEnabled;

    @Value("${orbisops.admin.auth.revocation.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;
    private volatile boolean unavailableLogged;

    public JwtRevocationRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean available() {
        return template() != null;
    }

    @Override
    public boolean isRevoked(String jwtId) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            ensureTable(jdbcTemplate);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM admin_auth_revoked_token WHERE jwt_id = ? AND expires_at > NOW()",
                    Integer.class,
                    jwtId);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            logFallback(e);
            return false;
        }
    }

    @Override
    public void revoke(String jwtId, String subject, Instant expiresAt) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return;
        }
        try {
            ensureTable(jdbcTemplate);
            jdbcTemplate.update("""
                            INSERT INTO admin_auth_revoked_token (jwt_id, subject, expires_at)
                            VALUES (?, ?, ?)
                            ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), update_time = CURRENT_TIMESTAMP
                            """,
                    jwtId,
                    subject,
                    Date.from(expiresAt));
        } catch (DataAccessException e) {
            logFallback(e);
        }
    }

    private JdbcTemplate template() {
        if (!jdbcEnabled) {
            return null;
        }
        return jdbcTemplateProvider.getIfAvailable();
    }

    private void ensureTable(JdbcTemplate jdbcTemplate) {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            if (autoInit) {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS admin_auth_revoked_token (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                          jwt_id VARCHAR(64) NOT NULL COMMENT 'JWT ID',
                          subject VARCHAR(128) NULL COMMENT '登录用户名',
                          expires_at DATETIME NOT NULL COMMENT '令牌过期时间',
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_jwt_id (jwt_id),
                          KEY idx_expires_at (expires_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台JWT撤销表'
                        """);
            }
            initialized = true;
        }
    }

    private void logFallback(Exception e) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("后台 JWT 撤销表不可用，注销/撤销能力降级：{}", e.getMessage());
        }
    }
}
