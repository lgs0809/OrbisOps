package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationRunPort;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MySQL-backed migration single-flight ledger and canonical report store. */
@Component
public final class JdbcPlatformCapabilityMigrationRunAdapter
        implements PlatformCapabilityMigrationRunPort {

    private static final String RUNNING = "RUNNING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    private final JdbcTemplate jdbc;
    private final boolean autoInit;

    public JdbcPlatformCapabilityMigrationRunAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            @Value("${orbisops.platform-migration.auto-init:true}") boolean autoInit) {
        this.jdbc = provider.getIfAvailable();
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (jdbc == null || !autoInit) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_platform_migration_run (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  migration_id VARCHAR(160) NOT NULL,
                  command_hash VARCHAR(64) NOT NULL,
                  actor VARCHAR(128) NOT NULL,
                  dry_run TINYINT NOT NULL DEFAULT 1,
                  batch_size INT NOT NULL,
                  steps_json TEXT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  owner_token VARCHAR(128) NOT NULL DEFAULT '',
                  fencing_token BIGINT NOT NULL DEFAULT 1,
                  lease_expires_at DATETIME(6) NULL,
                  successful TINYINT NOT NULL DEFAULT 0,
                  manual_review_required TINYINT NOT NULL DEFAULT 0,
                  report_hash VARCHAR(64) NOT NULL DEFAULT '',
                  report_json MEDIUMTEXT NULL,
                  error_code VARCHAR(96) NOT NULL DEFAULT '',
                  error_message TEXT NULL,
                  started_at DATETIME(6) NOT NULL,
                  finished_at DATETIME(6) NULL,
                  updated_at DATETIME(6) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_platform_migration_id (migration_id),
                  KEY idx_platform_migration_status (status, lease_expires_at),
                  KEY idx_platform_migration_updated (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台能力迁移运行与报告账本'
                """);
    }

    @Override
    public Claim claim(ClaimCommand command) {
        if (command == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_CLAIM_COMMAND_REQUIRED");
        JdbcTemplate store = requireJdbc();
        int inserted = store.update("""
                        INSERT IGNORE INTO ai_ops_platform_migration_run (
                          migration_id, command_hash, actor, dry_run, batch_size, steps_json,
                          status, owner_token, fencing_token, lease_expires_at, started_at, updated_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                command.migrationId(),
                command.commandHash(),
                command.actor(),
                command.dryRun() ? 1 : 0,
                command.batchSize(),
                JSON.toJSONString(command.steps().stream().map(Enum::name).toList()),
                RUNNING,
                command.ownerToken(),
                1L,
                timestamp(command.leaseExpiresAt()),
                timestamp(command.claimedAt()),
                timestamp(command.claimedAt()));
        RunSnapshot current = require(command.migrationId());
        if (!current.commandHash().equals(command.commandHash())) {
            return Claim.rejected(ClaimDisposition.CONFLICT, current);
        }
        if (inserted == 1) return Claim.acquired(current.fencingToken());
        if (COMPLETED.equals(current.status())) return Claim.completed(current);
        boolean leaseExpired = current.leaseExpiresAt() == null
                || !current.leaseExpiresAt().isAfter(command.claimedAt());
        if ((RUNNING.equals(current.status()) && leaseExpired) || FAILED.equals(current.status())) {
            int updated = store.update("""
                            UPDATE ai_ops_platform_migration_run
                               SET status=?, owner_token=?, fencing_token=fencing_token+1,
                                   lease_expires_at=?, error_code='', error_message='',
                                   finished_at=NULL, updated_at=?
                             WHERE migration_id=? AND command_hash=? AND fencing_token=?
                               AND (status=? OR (status=? AND (lease_expires_at IS NULL OR lease_expires_at<=?)))
                            """,
                    RUNNING,
                    command.ownerToken(),
                    timestamp(command.leaseExpiresAt()),
                    timestamp(command.claimedAt()),
                    command.migrationId(),
                    command.commandHash(),
                    current.fencingToken(),
                    FAILED,
                    RUNNING,
                    timestamp(command.claimedAt()));
            if (updated == 1) return Claim.acquired(current.fencingToken() + 1L);
            current = require(command.migrationId());
            if (COMPLETED.equals(current.status())) return Claim.completed(current);
        }
        return Claim.rejected(ClaimDisposition.IN_PROGRESS, current);
    }

    @Override
    public void complete(CompleteCommand command) {
        if (command == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_COMPLETE_COMMAND_REQUIRED");
        int updated = requireJdbc().update("""
                        UPDATE ai_ops_platform_migration_run
                           SET status=?, successful=?, manual_review_required=?, report_hash=?, report_json=?,
                               lease_expires_at=NULL, finished_at=?, updated_at=?
                         WHERE migration_id=? AND owner_token=? AND fencing_token=? AND status=?
                        """,
                COMPLETED,
                command.report().successful() ? 1 : 0,
                command.report().manualReviewRequired() ? 1 : 0,
                command.report().reportHash(),
                JSON.toJSONString(command.reportView()),
                timestamp(command.report().finishedAt()),
                timestamp(command.report().finishedAt()),
                command.migrationId(),
                command.ownerToken(),
                command.fencingToken(),
                RUNNING);
        if (updated != 1) {
            throw new IllegalStateException("PLATFORM_MIGRATION_COMPLETION_FENCED");
        }
    }

    @Override
    public void fail(FailCommand command) {
        if (command == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_FAIL_COMMAND_REQUIRED");
        int updated = requireJdbc().update("""
                        UPDATE ai_ops_platform_migration_run
                           SET status=?, error_code=?, error_message=?, lease_expires_at=NULL,
                               finished_at=?, updated_at=?
                         WHERE migration_id=? AND owner_token=? AND fencing_token=? AND status=?
                        """,
                FAILED,
                command.errorCode(),
                command.errorMessage(),
                timestamp(command.failedAt()),
                timestamp(command.failedAt()),
                command.migrationId(),
                command.ownerToken(),
                command.fencingToken(),
                RUNNING);
        if (updated != 1) {
            throw new IllegalStateException("PLATFORM_MIGRATION_FAILURE_FENCED");
        }
    }

    @Override
    public Optional<RunSnapshot> find(String migrationId) {
        String id = requiredText(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
        List<RunSnapshot> rows = requireJdbc().query("""
                        SELECT migration_id, command_hash, actor, dry_run, batch_size, steps_json,
                               status, fencing_token, lease_expires_at, successful,
                               manual_review_required, report_hash, report_json, error_code,
                               error_message, started_at, finished_at, updated_at
                          FROM ai_ops_platform_migration_run
                         WHERE migration_id=?
                        """,
                (rs, rowNum) -> row(rs), id);
        if (rows.size() > 1) throw new IllegalStateException("PLATFORM_MIGRATION_RUN_DUPLICATE:" + id);
        return rows.stream().findFirst();
    }

    @Override
    public List<RunSnapshot> list(int limit) {
        return List.copyOf(requireJdbc().query("""
                        SELECT migration_id, command_hash, actor, dry_run, batch_size, steps_json,
                               status, fencing_token, lease_expires_at, successful,
                               manual_review_required, report_hash, report_json, error_code,
                               error_message, started_at, finished_at, updated_at
                          FROM ai_ops_platform_migration_run
                         ORDER BY updated_at DESC, id DESC
                         LIMIT ?
                        """,
                (rs, rowNum) -> row(rs), Math.max(1, Math.min(limit, 200))));
    }

    private RunSnapshot require(String migrationId) {
        return find(migrationId).orElseThrow(() -> new IllegalStateException(
                "PLATFORM_MIGRATION_RUN_MISSING_AFTER_CLAIM:" + migrationId));
    }

    private RunSnapshot row(ResultSet rs) throws SQLException {
        return new RunSnapshot(
                rs.getString("migration_id"),
                rs.getString("command_hash"),
                rs.getString("actor"),
                rs.getBoolean("dry_run"),
                rs.getInt("batch_size"),
                steps(rs.getString("steps_json")),
                rs.getString("status"),
                rs.getLong("fencing_token"),
                instant(rs.getTimestamp("lease_expires_at")),
                rs.getBoolean("successful"),
                rs.getBoolean("manual_review_required"),
                rs.getString("report_hash"),
                objectMap(rs.getString("report_json")),
                rs.getString("error_code"),
                rs.getString("error_message"),
                requiredInstant(rs.getTimestamp("started_at"), "PLATFORM_MIGRATION_STARTED_AT_MISSING"),
                instant(rs.getTimestamp("finished_at")),
                requiredInstant(rs.getTimestamp("updated_at"), "PLATFORM_MIGRATION_UPDATED_AT_MISSING"));
    }

    private List<PlatformCapabilityMigrationStep> steps(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<?> values = JSON.parseArray(json);
        List<PlatformCapabilityMigrationStep> result = new ArrayList<>();
        for (Object value : values) {
            result.add(PlatformCapabilityMigrationStep.valueOf(String.valueOf(value)));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> objectMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Object parsed = JSON.parse(json);
        if (!(parsed instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return Map.copyOf(result);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbc == null) {
            throw new IllegalStateException("PLATFORM_MIGRATION_RUN_STORE_UNAVAILABLE");
        }
        return jdbc;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Instant requiredInstant(Timestamp value, String reasonCode) {
        if (value == null) throw new IllegalStateException(reasonCode);
        return value.toInstant();
    }

    private String requiredText(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
