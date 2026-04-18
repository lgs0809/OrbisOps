package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionRecord;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionTokenRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OpsChannelApprovalActionRepository implements ChannelApprovalActionTokenRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public OpsChannelApprovalActionRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public void ensureSchema() {
        requiredTemplate().execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_approval_action (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  action_id VARCHAR(96) NOT NULL,
                  token_hash CHAR(64) NOT NULL,
                  channel_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL,
                  package_id VARCHAR(96) NOT NULL,
                  package_version INT NOT NULL,
                  package_hash VARCHAR(128) NOT NULL,
                  decision VARCHAR(16) NOT NULL,
                  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                  issued_by VARCHAR(128) NOT NULL,
                  expires_at DATETIME(3) NOT NULL,
                  created_at DATETIME(3) NOT NULL,
                  consumed_by VARCHAR(128) NOT NULL DEFAULT '',
                  consumed_at DATETIME(3) NULL,
                  terminal_reason VARCHAR(256) NOT NULL DEFAULT '',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_channel_approval_action_id (action_id),
                  UNIQUE KEY uk_channel_approval_token_hash (token_hash),
                  KEY idx_channel_approval_package (project_id, package_id, package_version, status),
                  KEY idx_channel_approval_expiry (status, expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel opaque approval action token ledger'
                """);
        requiredTemplate().execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_approval_action_actor (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  token_hash CHAR(64) NOT NULL,
                  actor VARCHAR(128) NOT NULL,
                  claimed_at DATETIME(3) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_channel_approval_action_actor (token_hash, actor),
                  KEY idx_channel_approval_actor (actor, claimed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-actor idempotency for Channel approval actions'
                """);
    }

    @Override
    public boolean insert(ChannelApprovalActionRecord record) {
        if (record == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_ACTION_REQUIRED");
        return requiredTemplate().update("""
                INSERT INTO ai_ops_channel_approval_action
                  (action_id,token_hash,channel_id,project_id,package_id,package_version,package_hash,decision,status,
                   issued_by,expires_at,created_at,consumed_by,consumed_at,terminal_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                record.actionId(), record.tokenHash(), record.channelId(), record.projectId(), record.packageId(),
                record.packageVersion(), record.packageHash(), record.decision().name(), record.status().name(),
                record.issuedBy(), timestamp(record.expiresAt()), timestamp(record.createdAt()), record.consumedBy(),
                timestamp(record.consumedAt()), record.terminalReason()) == 1;
    }

    @Override
    public Optional<ChannelApprovalActionRecord> findByTokenHash(String tokenHash) {
        List<ChannelApprovalActionRecord> rows = requiredTemplate().query("""
                SELECT * FROM ai_ops_channel_approval_action WHERE token_hash=? LIMIT 1
                """, this::record, tokenHash);
        return rows.stream().findFirst();
    }

    @Override
    public boolean claimForActor(String tokenHash, String actor, Instant claimedAt) {
        try {
            return requiredTemplate().update("""
                    INSERT INTO ai_ops_channel_approval_action_actor (token_hash,actor,claimed_at)
                    VALUES (?,?,?)
                    """, tokenHash, text(actor), timestamp(claimedAt)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public boolean releaseActorClaim(String tokenHash, String actor) {
        return requiredTemplate().update("""
                DELETE FROM ai_ops_channel_approval_action_actor WHERE token_hash=? AND actor=?
                """, tokenHash, text(actor)) == 1;
    }

    @Override
    public boolean complete(String tokenHash, String actor, Instant consumedAt) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_approval_action
                SET status='CONSUMED',consumed_by=?,consumed_at=?,terminal_reason=''
                WHERE token_hash=? AND status IN ('ACTIVE','PROCESSING')
                """, text(actor), timestamp(consumedAt), tokenHash) == 1;
    }

    @Override
    public boolean revoke(String tokenHash, String actor, String reason, Instant revokedAt) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_approval_action
                SET status='REVOKED',consumed_by=?,consumed_at=?,terminal_reason=?
                WHERE token_hash=? AND status IN ('ACTIVE','PROCESSING')
                """, text(actor), timestamp(revokedAt), limit(reason, 256), tokenHash) == 1;
    }

    private ChannelApprovalActionRecord record(ResultSet rs, int rowNum) throws SQLException {
        return new ChannelApprovalActionRecord(
                rs.getString("action_id"),
                rs.getString("token_hash"),
                rs.getString("channel_id"),
                rs.getString("project_id"),
                rs.getString("package_id"),
                rs.getInt("package_version"),
                rs.getString("package_hash"),
                ChannelApprovalActionRecord.Decision.valueOf(rs.getString("decision")),
                ChannelApprovalActionRecord.Status.valueOf(rs.getString("status")),
                rs.getString("issued_by"),
                instant(rs, "expires_at"),
                instant(rs, "created_at"),
                rs.getString("consumed_by"),
                instant(rs, "consumed_at"),
                rs.getString("terminal_reason"));
    }

    private JdbcTemplate requiredTemplate() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANNEL_APPROVAL_ACTION_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String limit(String value, int max) {
        String safe = text(value);
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
