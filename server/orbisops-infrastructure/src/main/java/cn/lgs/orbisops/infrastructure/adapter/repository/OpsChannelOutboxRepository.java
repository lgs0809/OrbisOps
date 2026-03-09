package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OpsChannelOutboxRepository implements IChannelOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OpsChannelOutboxRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    public void ensureSchema() {
        JdbcTemplate template = requiredTemplate();
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_notification_outbox (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,dedup_key VARCHAR(320) NOT NULL,
                  project_id VARCHAR(128) NOT NULL,channel_id VARCHAR(80) NOT NULL,target VARCHAR(256) NOT NULL,
                  analysis_id VARCHAR(128) NOT NULL DEFAULT '',status VARCHAR(32) NOT NULL,
                  request_json MEDIUMTEXT NULL,response_json MEDIUMTEXT NULL,
                  message_text MEDIUMTEXT NULL,metadata_json TEXT NULL,content_hash CHAR(64) NOT NULL DEFAULT '',
                  retry_count INT NOT NULL DEFAULT 0,next_retry_at DATETIME NOT NULL,locked_token VARCHAR(64) NOT NULL DEFAULT '',
                  lease_expires_at DATETIME NULL,last_attempt_at DATETIME NULL,dead_letter_at DATETIME NULL,
                  last_response MEDIUMTEXT NULL,last_error TEXT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id),UNIQUE KEY uk_channel_notification_dedup(dedup_key),
                  KEY idx_channel_notification_retry(status,next_retry_at),KEY idx_channel_notification_project(project_id,create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel主动通知Outbox'
                """);
        ensureColumn("lease_expires_at", "DATETIME NULL AFTER locked_token");
        ensureColumn("last_attempt_at", "DATETIME NULL AFTER lease_expires_at");
        ensureColumn("dead_letter_at", "DATETIME NULL AFTER last_attempt_at");
    }

    @Override
    public long enqueue(ChannelOutboxRecord record) {
        JdbcTemplate template = requiredTemplate();
        template.update("""
                INSERT INTO ai_ops_channel_notification_outbox
                  (dedup_key,project_id,channel_id,target,analysis_id,status,message_text,metadata_json,content_hash,retry_count,next_retry_at)
                VALUES (?,?,?,?,?,'PENDING',?,?,?,0,NOW())
                ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
                """, record.dedupKey(), record.projectId(), record.channelId(), record.target(), record.analysisId(),
                record.messageText(), record.metadataJson(), record.contentHash());
        Long id = template.queryForObject(
                "SELECT id FROM ai_ops_channel_notification_outbox WHERE dedup_key=?", Long.class, record.dedupKey());
        if (id == null) throw new IllegalStateException("CHANNEL_OUTBOX_ENQUEUE_FAILED");
        return id;
    }

    @Override
    public void recoverExpiredLeases(int maxAttempts) {
        requiredTemplate().update("""
                UPDATE ai_ops_channel_notification_outbox
                SET status=CASE WHEN retry_count>=? THEN 'DEAD_LETTER' ELSE 'FAILED' END,
                    dead_letter_at=CASE WHEN retry_count>=? THEN CURRENT_TIMESTAMP ELSE dead_letter_at END,
                    locked_token='',lease_expires_at=NULL,next_retry_at=NOW(),
                    last_error='CHANNEL_OUTBOX_LEASE_EXPIRED',update_time=CURRENT_TIMESTAMP
                WHERE status='RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at<NOW()
                """, maxAttempts, maxAttempts);
    }

    @Override
    public List<Long> findDispatchableIds(int maxAttempts, int limit) {
        return requiredTemplate().queryForList("""
                SELECT id FROM ai_ops_channel_notification_outbox
                WHERE status IN ('PENDING','FAILED') AND retry_count<? AND next_retry_at<=NOW()
                ORDER BY id ASC LIMIT ?
                """, Long.class, maxAttempts, Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public boolean tryAcquireLease(long id, String lockToken, LocalDateTime leaseExpiresAt, int maxAttempts) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_notification_outbox
                SET status='RUNNING',locked_token=?,lease_expires_at=?,last_attempt_at=CURRENT_TIMESTAMP,
                    update_time=CURRENT_TIMESTAMP
                WHERE id=? AND status IN ('PENDING','FAILED') AND retry_count<? AND next_retry_at<=NOW()
                """, lockToken, leaseExpiresAt, id, maxAttempts) == 1;
    }

    @Override
    public Optional<ChannelOutboxRecord> findById(long id) {
        return requiredTemplate().query("SELECT * FROM ai_ops_channel_notification_outbox WHERE id=? LIMIT 1",
                this::record, id).stream().findFirst();
    }

    @Override
    public Optional<ChannelOutboxRecord> findByProjectAndId(String projectId, long id) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_channel_notification_outbox WHERE id=? AND project_id=? LIMIT 1
                """, this::record, id, projectId).stream().findFirst();
    }

    @Override
    public List<ChannelOutboxRecord> findByProject(String projectId, int limit) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_channel_notification_outbox WHERE project_id=? ORDER BY id DESC LIMIT ?
                """, this::record, projectId, Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public void markSucceeded(long id, String lockToken, String response) {
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_notification_outbox
                SET status='SUCCEEDED',locked_token='',lease_expires_at=NULL,last_response=?,last_error='',
                    update_time=CURRENT_TIMESTAMP
                WHERE id=? AND locked_token=?
                """, response, id, lockToken);
        if (updated != 1) throw new IllegalStateException("CHANNEL_OUTBOX_LEASE_LOST");
    }

    @Override
    public void markFailed(long id, String lockToken, int retryCount, LocalDateTime nextRetryAt,
                           boolean deadLetter, String error) {
        String status = deadLetter ? "DEAD_LETTER" : "FAILED";
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_notification_outbox
                SET status=?,locked_token='',lease_expires_at=NULL,retry_count=?,next_retry_at=?,
                    dead_letter_at=CASE WHEN ?='DEAD_LETTER' THEN CURRENT_TIMESTAMP ELSE dead_letter_at END,
                    last_error=?,update_time=CURRENT_TIMESTAMP
                WHERE id=? AND locked_token=?
                """, status, retryCount, nextRetryAt, status, error, id, lockToken);
        if (updated != 1) throw new IllegalStateException("CHANNEL_OUTBOX_LEASE_LOST");
    }

    @Override
    public boolean requeue(String projectId, long id) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_notification_outbox
                SET status='PENDING',retry_count=0,next_retry_at=NOW(),locked_token='',lease_expires_at=NULL,
                    dead_letter_at=NULL,last_error='',update_time=CURRENT_TIMESTAMP
                WHERE id=? AND project_id=? AND status IN ('DEAD_LETTER','FAILED')
                """, id, projectId) == 1;
    }

    @Override
    public boolean cancel(String projectId, long id) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_notification_outbox
                SET status='CANCELLED',locked_token='',lease_expires_at=NULL,update_time=CURRENT_TIMESTAMP
                WHERE id=? AND project_id=? AND status IN ('DEAD_LETTER','FAILED','PENDING')
                """, id, projectId) == 1;
    }

    @Override
    public ChannelOutboxStatus status(String projectId) {
        JdbcTemplate template = requiredTemplate();
        return new ChannelOutboxStatus(count(template,
                "SELECT COUNT(1) FROM ai_ops_channel_notification_outbox WHERE project_id=? AND status IN ('PENDING','FAILED','RUNNING')", projectId),
                count(template, "SELECT COUNT(1) FROM ai_ops_channel_notification_outbox WHERE project_id=? AND status='DEAD_LETTER'", projectId));
    }

    @Override
    public ChannelOutboxStatus statusAll() {
        JdbcTemplate template = requiredTemplate();
        return new ChannelOutboxStatus(count(template,
                "SELECT COUNT(1) FROM ai_ops_channel_notification_outbox WHERE status IN ('PENDING','FAILED','RUNNING')"),
                count(template, "SELECT COUNT(1) FROM ai_ops_channel_notification_outbox WHERE status='DEAD_LETTER'"));
    }

    private int count(JdbcTemplate template, String sql, Object... args) {
        Integer value = template.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private ChannelOutboxRecord record(ResultSet rs, int rowNum) throws SQLException {
        return new ChannelOutboxRecord(
                rs.getLong("id"), rs.getString("dedup_key"), rs.getString("project_id"), rs.getString("channel_id"),
                rs.getString("target"), rs.getString("analysis_id"), rs.getString("status"), rs.getString("message_text"),
                rs.getString("metadata_json"), rs.getString("content_hash"), rs.getInt("retry_count"),
                rs.getString("last_error"), rs.getString("last_response"), localDateTime(rs, "next_retry_at"),
                localDateTime(rs, "lease_expires_at"), localDateTime(rs, "dead_letter_at"),
                localDateTime(rs, "last_attempt_at"), instant(rs, "create_time"), instant(rs, "update_time"));
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void ensureColumn(String column, String definition) {
        Integer count = requiredTemplate().queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_channel_notification_outbox' AND COLUMN_NAME=?
                """, Integer.class, column);
        if (count == null || count == 0) {
            requiredTemplate().execute("ALTER TABLE ai_ops_channel_notification_outbox ADD COLUMN " + column + " " + definition);
        }
    }

    private JdbcTemplate requiredTemplate() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }
}
