package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MySQL-backed idempotency ledger with lease takeover and fencing-token CAS. */
@Component
public class JdbcToolExecutionIdempotencyAdapter implements ToolExecutionIdempotencyPort {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_FAILED_RETRYABLE = "FAILED_RETRYABLE";
    private static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String PROJECTION_PENDING = "PENDING";
    private static final String PROJECTION_SUCCEEDED = "SUCCEEDED";
    private static final String PROJECTION_FAILED = "FAILED";
    private static final String PROJECTION_NOT_REQUIRED = "NOT_REQUIRED";

    private final JdbcTemplate jdbc;
    private final boolean autoInit;

    public JdbcToolExecutionIdempotencyAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            @Value("${orbisops.tool-execution.idempotency.auto-init:true}") boolean autoInit) {
        this.jdbc = provider.getIfAvailable();
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (jdbc == null || !autoInit) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_ledger (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  idempotency_key VARCHAR(200) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  run_id VARCHAR(100) NOT NULL DEFAULT '',
                  node_id VARCHAR(128) NOT NULL DEFAULT '',
                  attempt_no INT NOT NULL DEFAULT 0,
                  tool_call_index INT NOT NULL DEFAULT 0,
                  input_hash VARCHAR(64) NOT NULL,
                  target_hash VARCHAR(64) NOT NULL,
                  side_effecting TINYINT NOT NULL DEFAULT 0,
                  reconciliation_json MEDIUMTEXT NULL,
                  status VARCHAR(32) NOT NULL,
                  owner_token VARCHAR(128) NOT NULL DEFAULT '',
                  fencing_token BIGINT NOT NULL DEFAULT 1,
                  lease_expires_at DATETIME(6) NULL,
                  allowed TINYINT NOT NULL DEFAULT 0,
                  decision_code VARCHAR(64) NOT NULL DEFAULT '',
                  result_id VARCHAR(100) NOT NULL DEFAULT '',
                  evidence_id VARCHAR(100) NOT NULL DEFAULT '',
                  preview_text MEDIUMTEXT NULL,
                  output_hash VARCHAR(64) NOT NULL DEFAULT '',
                  truncated TINYINT NOT NULL DEFAULT 0,
                  full_output_ref VARCHAR(256) NOT NULL DEFAULT '',
                  recorded_input_hash VARCHAR(64) NOT NULL DEFAULT '',
                  duration_ms BIGINT NOT NULL DEFAULT 0,
                  payload_json MEDIUMTEXT NULL,
                  error_code VARCHAR(96) NOT NULL DEFAULT '',
                  error_message TEXT NULL,
                  created_at DATETIME(6) NOT NULL,
                  updated_at DATETIME(6) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_tool_execution_idempotency (idempotency_key),
                  KEY idx_tool_execution_run (project_id, run_id, updated_at),
                  KEY idx_tool_execution_status (status, lease_expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具执行幂等与副作用对账账本'
                """);
        ensureLedgerColumn("side_effecting", "TINYINT NOT NULL DEFAULT 0 AFTER target_hash");
        ensureLedgerColumn("reconciliation_json", "MEDIUMTEXT NULL AFTER side_effecting");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_completion_projection (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  projection_id VARCHAR(96) NOT NULL,
                  idempotency_key VARCHAR(200) NOT NULL,
                  projection_json MEDIUMTEXT NOT NULL,
                  checkpoint_status VARCHAR(24) NOT NULL,
                  audit_status VARCHAR(24) NOT NULL,
                  owner_token VARCHAR(128) NOT NULL DEFAULT '',
                  fencing_token BIGINT NOT NULL DEFAULT 0,
                  lease_expires_at DATETIME(6) NULL,
                  attempts INT NOT NULL DEFAULT 0,
                  next_attempt_at DATETIME(6) NOT NULL,
                  last_error TEXT NULL,
                  created_at DATETIME(6) NOT NULL,
                  updated_at DATETIME(6) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_tool_completion_projection (projection_id),
                  UNIQUE KEY uk_tool_completion_idempotency (idempotency_key),
                  KEY idx_tool_completion_pending
                    (checkpoint_status, audit_status, next_attempt_at, updated_at),
                  KEY idx_tool_completion_claim
                    (next_attempt_at, lease_expires_at, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COMMENT='ToolResult/Evidence 权威完成后的 Checkpoint/Audit 投影对账'
                """);
        ensureProjectionColumn(
                "owner_token", "VARCHAR(128) NOT NULL DEFAULT '' AFTER audit_status");
        ensureProjectionColumn(
                "fencing_token", "BIGINT NOT NULL DEFAULT 0 AFTER owner_token");
        ensureProjectionColumn(
                "lease_expires_at", "DATETIME(6) NULL AFTER fencing_token");
        ensureProjectionIndex();
    }

    @Override
    public Reservation reserve(ReserveCommand command) {
        if (command == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_RESERVE_COMMAND_REQUIRED");
        JdbcTemplate store = requireJdbc();
        int inserted = store.update("""
                        INSERT IGNORE INTO ai_ops_tool_execution_ledger (
                          idempotency_key, project_id, run_id, node_id, attempt_no, tool_call_index,
                          input_hash, target_hash, side_effecting, reconciliation_json,
                          status, owner_token, fencing_token, lease_expires_at, created_at, updated_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                command.idempotencyKey(), command.projectId(), command.runId(), command.nodeId(),
                command.attempt(), command.toolCallIndex(), command.inputHash(), command.targetHash(),
                command.sideEffecting(), JSON.toJSONString(command.reconciliationContext()),
                STATUS_RUNNING, command.ownerToken(), 1L,
                timestamp(command.leaseExpiresAt()), timestamp(command.reservedAt()), timestamp(command.reservedAt()));
        LedgerRow row = requireRow(command.idempotencyKey());
        if (!row.inputHash().equals(command.inputHash()) || !row.targetHash().equals(command.targetHash())) {
            return Reservation.rejected(Disposition.CONFLICT, "TOOL_EXECUTION_IDEMPOTENCY_CONFLICT");
        }
        if (inserted == 1) return Reservation.execute(row.fencingToken());
        return decideExisting(row, command, true);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void complete(CompleteCommand command) {
        if (command == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_COMPLETE_COMMAND_REQUIRED");
        ToolExecutionRecordedResult recorded = command.recorded();
        int updated = requireJdbc().update("""
                        UPDATE ai_ops_tool_execution_ledger
                           SET status=?, allowed=?, decision_code=?, result_id=?, evidence_id=?, preview_text=?,
                               output_hash=?, truncated=?, full_output_ref=?, recorded_input_hash=?, duration_ms=?,
                               payload_json=?, error_code='', error_message='', lease_expires_at=NULL, updated_at=?
                         WHERE idempotency_key=? AND owner_token=? AND fencing_token=? AND status=?
                        """,
                command.allowed() ? STATUS_SUCCEEDED : STATUS_BLOCKED,
                command.allowed() ? 1 : 0,
                command.decision(),
                recorded.resultId(),
                recorded.evidenceId(),
                recorded.preview(),
                recorded.outputHash(),
                recorded.truncated() ? 1 : 0,
                recorded.fullOutputRef(),
                recorded.inputHash(),
                recorded.durationMs(),
                JSON.toJSONString(command.payload()),
                timestamp(command.completedAt()),
                command.idempotencyKey(), command.ownerToken(), command.fencingToken(), STATUS_RUNNING);
        if (updated != 1) {
            throw new IllegalStateException("TOOL_EXECUTION_IDEMPOTENCY_COMPLETION_FENCED");
        }
        if (command.projection() != null) {
            enqueueProjection(command.idempotencyKey(), command.projection(), command.completedAt());
        }
    }

    @Override
    public void fail(FailCommand command) {
        if (command == null) throw new IllegalArgumentException("TOOL_IDEMPOTENCY_FAIL_COMMAND_REQUIRED");
        String status = command.uncertainSideEffect() ? STATUS_REVIEW_REQUIRED : STATUS_FAILED_RETRYABLE;
        int updated = requireJdbc().update("""
                        UPDATE ai_ops_tool_execution_ledger
                           SET status=?, error_code=?, error_message=?, lease_expires_at=?, updated_at=?
                         WHERE idempotency_key=? AND owner_token=? AND fencing_token=? AND status=?
                        """,
                status,
                command.errorCode(),
                command.errorMessage(),
                timestamp(command.failedAt()),
                timestamp(command.failedAt()),
                command.idempotencyKey(), command.ownerToken(), command.fencingToken(), STATUS_RUNNING);
        if (updated != 1) {
            throw new IllegalStateException("TOOL_EXECUTION_IDEMPOTENCY_FAILURE_FENCED");
        }
    }

    @Override
    public boolean hasUnresolvedSideEffect(String projectId, String runId) {
        String normalizedProject = text(projectId);
        String normalizedRun = text(runId);
        if (normalizedProject.isBlank() || normalizedRun.isBlank()) return false;
        quarantineExpiredSideEffects(normalizedProject, normalizedRun);
        Integer count = requireJdbc().queryForObject("""
                SELECT COUNT(*)
                  FROM ai_ops_tool_execution_ledger
                 WHERE project_id=? AND run_id=? AND status=?
                """, Integer.class, normalizedProject, normalizedRun, STATUS_REVIEW_REQUIRED);
        return count != null && count > 0;
    }

    @Override
    public List<UnresolvedSideEffect> unresolvedSideEffects(String projectId, String runId) {
        String normalizedProject = text(projectId);
        String normalizedRun = text(runId);
        if (normalizedProject.isBlank() || normalizedRun.isBlank()) return List.of();
        quarantineExpiredSideEffects(normalizedProject, normalizedRun);
        return requireJdbc().query("""
                        SELECT idempotency_key, project_id, run_id, node_id, input_hash, target_hash,
                               reconciliation_json, error_code, error_message, updated_at
                          FROM ai_ops_tool_execution_ledger
                         WHERE project_id=? AND run_id=? AND status=?
                         ORDER BY updated_at ASC, id ASC
                        """,
                (rs, rowNum) -> new UnresolvedSideEffect(
                        rs.getString("idempotency_key"),
                        rs.getString("project_id"),
                        rs.getString("run_id"),
                        rs.getString("node_id"),
                        rs.getString("input_hash"),
                        rs.getString("target_hash"),
                        parseObject(rs.getString("reconciliation_json")),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        rs.getTimestamp("updated_at").toInstant()),
                normalizedProject, normalizedRun, STATUS_REVIEW_REQUIRED);
    }

    @Override
    public List<UnresolvedSideEffect> unresolvedSideEffects(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        quarantineExpiredSideEffects();
        return requireJdbc().query("""
                        SELECT idempotency_key, project_id, run_id, node_id, input_hash, target_hash,
                               reconciliation_json, error_code, error_message, updated_at
                          FROM ai_ops_tool_execution_ledger
                         WHERE status=?
                         ORDER BY updated_at ASC, id ASC
                         LIMIT ?
                        """,
                (rs, rowNum) -> new UnresolvedSideEffect(
                        rs.getString("idempotency_key"),
                        rs.getString("project_id"),
                        rs.getString("run_id"),
                        rs.getString("node_id"),
                        rs.getString("input_hash"),
                        rs.getString("target_hash"),
                        parseObject(rs.getString("reconciliation_json")),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        rs.getTimestamp("updated_at").toInstant()),
                STATUS_REVIEW_REQUIRED, safeLimit);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void resolveSideEffect(ResolveSideEffectCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_EXECUTION_SIDE_EFFECT_RESOLUTION_REQUIRED");
        }
        String payload = JSON.toJSONString(Map.of(
                "resolution", command.resolution().name(),
                "evidenceId", command.evidenceId(),
                "evidenceHash", command.evidenceHash(),
                "note", command.note(),
                "actor", command.actor(),
                "resolvedAt", command.resolvedAt().toString()));
        int updated;
        if (command.resolution() == SideEffectResolution.CONFIRMED_SUCCEEDED) {
            String resultId = "reconciled-" + command.evidenceHash().substring(0, 24);
            updated = requireJdbc().update("""
                    UPDATE ai_ops_tool_execution_ledger
                       SET status=?, allowed=1, decision_code='RECONCILED_SUCCEEDED',
                           result_id=?, evidence_id=?, preview_text=?, output_hash=?, truncated=0,
                           full_output_ref=?, recorded_input_hash=input_hash, duration_ms=0,
                           payload_json=?, error_code='', error_message='', owner_token='',
                           lease_expires_at=NULL, updated_at=?
                     WHERE idempotency_key=? AND project_id=? AND run_id=? AND status=?
                    """,
                    STATUS_SUCCEEDED,
                    resultId,
                    command.evidenceId(),
                    command.note(),
                    command.evidenceHash(),
                    "evidence://" + command.evidenceId(),
                    payload,
                    timestamp(command.resolvedAt()),
                    command.idempotencyKey(),
                    command.projectId(),
                    command.runId(),
                    STATUS_REVIEW_REQUIRED);
        } else {
            updated = requireJdbc().update("""
                    UPDATE ai_ops_tool_execution_ledger
                       SET status=?, allowed=0, decision_code='RECONCILED_NOT_EXECUTED',
                           payload_json=?, error_code='MANUAL_RECONCILED_NOT_EXECUTED',
                           error_message=?, owner_token='', lease_expires_at=NULL, updated_at=?
                     WHERE idempotency_key=? AND project_id=? AND run_id=? AND status=?
                    """,
                    STATUS_FAILED_RETRYABLE,
                    payload,
                    command.note(),
                    timestamp(command.resolvedAt()),
                    command.idempotencyKey(),
                    command.projectId(),
                    command.runId(),
                    STATUS_REVIEW_REQUIRED);
        }
        if (updated != 1) {
            throw new IllegalStateException("TOOL_EXECUTION_SIDE_EFFECT_RESOLUTION_CONFLICT");
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Optional<ProjectionDelivery> claimProjection(
            ProjectionClaimCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_CLAIM_REQUIRED");
        }
        int updated = requireJdbc().update("""
                UPDATE ai_ops_tool_execution_completion_projection
                   SET owner_token=?, fencing_token=fencing_token+1,
                       lease_expires_at=?, updated_at=?
                 WHERE projection_id=? AND next_attempt_at<=?
                   AND (checkpoint_status IN (?,?) OR audit_status IN (?,?))
                   AND (owner_token='' OR lease_expires_at IS NULL OR lease_expires_at<=?)
                """,
                command.ownerToken(),
                timestamp(command.leaseExpiresAt()),
                timestamp(command.claimedAt()),
                command.projection().projectionId(),
                timestamp(command.claimedAt()),
                PROJECTION_PENDING,
                PROJECTION_FAILED,
                PROJECTION_PENDING,
                PROJECTION_FAILED,
                timestamp(command.claimedAt()));
        if (updated != 1) return Optional.empty();
        return findClaimedProjection(
                command.projection().projectionId(), command.ownerToken());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public List<ProjectionDelivery> claimProjections(
            ProjectionBatchClaimCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_BATCH_CLAIM_REQUIRED");
        }
        List<String> projectionIds = requireJdbc().query("""
                        SELECT projection_id
                          FROM ai_ops_tool_execution_completion_projection
                         WHERE next_attempt_at<=?
                           AND (checkpoint_status IN (?,?) OR audit_status IN (?,?))
                           AND (owner_token='' OR lease_expires_at IS NULL OR lease_expires_at<=?)
                         ORDER BY next_attempt_at ASC, id ASC
                         LIMIT ?
                         FOR UPDATE SKIP LOCKED
                        """,
                (rs, rowNum) -> rs.getString("projection_id"),
                timestamp(command.claimedAt()),
                PROJECTION_PENDING,
                PROJECTION_FAILED,
                PROJECTION_PENDING,
                PROJECTION_FAILED,
                timestamp(command.claimedAt()),
                command.limit());
        List<ProjectionDelivery> claimed = new ArrayList<>();
        for (String projectionId : projectionIds) {
            int updated = requireJdbc().update("""
                    UPDATE ai_ops_tool_execution_completion_projection
                       SET owner_token=?, fencing_token=fencing_token+1,
                           lease_expires_at=?, updated_at=?
                     WHERE projection_id=?
                       AND (owner_token='' OR lease_expires_at IS NULL OR lease_expires_at<=?)
                    """,
                    command.ownerToken(),
                    timestamp(command.leaseExpiresAt()),
                    timestamp(command.claimedAt()),
                    projectionId,
                    timestamp(command.claimedAt()));
            if (updated == 1) {
                findClaimedProjection(projectionId, command.ownerToken())
                        .ifPresent(claimed::add);
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public List<ProjectionDelivery> pendingProjections(
            Instant dueAt,
            int limit) {
        Instant due = dueAt == null ? Instant.now() : dueAt;
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return requireJdbc().query("""
                        SELECT projection_json, checkpoint_status, audit_status, attempts
                          FROM ai_ops_tool_execution_completion_projection
                         WHERE next_attempt_at<=?
                           AND (checkpoint_status IN (?,?) OR audit_status IN (?,?))
                         ORDER BY next_attempt_at ASC, id ASC
                         LIMIT ?
                        """,
                (rs, rowNum) -> new ProjectionDelivery(
                        decodeProjection(rs.getString("projection_json")),
                        pending(rs.getString("checkpoint_status")),
                        pending(rs.getString("audit_status")),
                        rs.getInt("attempts")),
                timestamp(due),
                PROJECTION_PENDING,
                PROJECTION_FAILED,
                PROJECTION_PENDING,
                PROJECTION_FAILED,
                boundedLimit);
    }

    @Override
    public void projectionSucceeded(ProjectionSucceededCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_SUCCESS_COMMAND_REQUIRED");
        }
        String column = projectionColumn(command.channel());
        boolean fenced = command.fencingToken() > 0L && !command.ownerToken().isBlank();
        int updated = fenced
                ? requireJdbc().update(
                        "UPDATE ai_ops_tool_execution_completion_projection SET " + column
                                + "=?, updated_at=? WHERE projection_id=? AND owner_token=?"
                                + " AND fencing_token=? AND " + column + "<>?",
                        PROJECTION_SUCCEEDED,
                        timestamp(command.completedAt()),
                        command.projectionId(),
                        command.ownerToken(),
                        command.fencingToken(),
                        PROJECTION_SUCCEEDED)
                : requireJdbc().update(
                        "UPDATE ai_ops_tool_execution_completion_projection SET " + column
                                + "=?, updated_at=? WHERE projection_id=? AND " + column + "<>?",
                        PROJECTION_SUCCEEDED,
                        timestamp(command.completedAt()),
                        command.projectionId(),
                        PROJECTION_SUCCEEDED);
        assertProjectionMutation(updated, fenced);
    }

    @Override
    public void projectionFailed(ProjectionFailedCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_FAILURE_COMMAND_REQUIRED");
        }
        String column = projectionColumn(command.channel());
        boolean fenced = command.fencingToken() > 0L && !command.ownerToken().isBlank();
        int updated = fenced
                ? requireJdbc().update(
                        "UPDATE ai_ops_tool_execution_completion_projection SET " + column
                                + "=?, attempts=attempts+1, next_attempt_at=?, last_error=?, updated_at=?"
                                + " WHERE projection_id=? AND owner_token=? AND fencing_token=?"
                                + " AND " + column + "<>?",
                        PROJECTION_FAILED,
                        timestamp(command.retryAt()),
                        command.errorMessage(),
                        timestamp(command.failedAt()),
                        command.projectionId(),
                        command.ownerToken(),
                        command.fencingToken(),
                        PROJECTION_SUCCEEDED)
                : requireJdbc().update(
                        "UPDATE ai_ops_tool_execution_completion_projection SET " + column
                                + "=?, attempts=attempts+1, next_attempt_at=?, last_error=?, updated_at=?"
                                + " WHERE projection_id=? AND " + column + "<>?",
                        PROJECTION_FAILED,
                        timestamp(command.retryAt()),
                        command.errorMessage(),
                        timestamp(command.failedAt()),
                        command.projectionId(),
                        PROJECTION_SUCCEEDED);
        assertProjectionMutation(updated, fenced);
    }

    @Override
    public void releaseProjection(ProjectionReleaseCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_PROJECTION_RELEASE_COMMAND_REQUIRED");
        }
        int updated = requireJdbc().update("""
                UPDATE ai_ops_tool_execution_completion_projection
                   SET owner_token='', lease_expires_at=NULL, updated_at=?
                 WHERE projection_id=? AND owner_token=? AND fencing_token=?
                """,
                timestamp(command.releasedAt()),
                command.projectionId(),
                command.ownerToken(),
                command.fencingToken());
        if (updated != 1) {
            throw new IllegalStateException("TOOL_COMPLETION_PROJECTION_RELEASE_FENCED");
        }
    }

    private Optional<ProjectionDelivery> findClaimedProjection(
            String projectionId,
            String ownerToken) {
        return requireJdbc().query("""
                        SELECT projection_json, checkpoint_status, audit_status, attempts,
                               owner_token, fencing_token, lease_expires_at
                          FROM ai_ops_tool_execution_completion_projection
                         WHERE projection_id=? AND owner_token=?
                        """,
                (rs, rowNum) -> projectionDelivery(rs),
                projectionId,
                ownerToken).stream().findFirst();
    }

    private ProjectionDelivery projectionDelivery(ResultSet rs) throws SQLException {
        Timestamp lease = rs.getTimestamp("lease_expires_at");
        return new ProjectionDelivery(
                decodeProjection(rs.getString("projection_json")),
                pending(rs.getString("checkpoint_status")),
                pending(rs.getString("audit_status")),
                rs.getInt("attempts"),
                rs.getString("owner_token"),
                rs.getLong("fencing_token"),
                lease == null ? null : lease.toInstant());
    }

    private void assertProjectionMutation(int updated, boolean fenced) {
        if (updated > 1) {
            throw new IllegalStateException("TOOL_COMPLETION_PROJECTION_DUPLICATE");
        }
        if (fenced && updated != 1) {
            throw new IllegalStateException("TOOL_COMPLETION_PROJECTION_FENCED");
        }
    }

    private void enqueueProjection(
            String idempotencyKey,
            CompletionProjection projection,
            Instant createdAt) {
        String checkpointStatus = projection.checkpointType().isBlank()
                ? PROJECTION_NOT_REQUIRED
                : PROJECTION_PENDING;
        requireJdbc().update("""
                        INSERT INTO ai_ops_tool_execution_completion_projection (
                          projection_id, idempotency_key, projection_json,
                          checkpoint_status, audit_status, attempts,
                          next_attempt_at, last_error, created_at, updated_at
                        ) VALUES (?,?,?,?,?,0,?,'',?,?)
                        ON DUPLICATE KEY UPDATE
                          projection_json=VALUES(projection_json),
                          updated_at=VALUES(updated_at)
                        """,
                projection.projectionId(),
                idempotencyKey,
                JSON.toJSONString(projectionMap(projection)),
                checkpointStatus,
                PROJECTION_PENDING,
                timestamp(createdAt),
                timestamp(createdAt),
                timestamp(createdAt));
    }

    private Map<String, Object> projectionMap(CompletionProjection projection) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("projectId", projection.auditEvent().projectId());
        audit.put("action", projection.auditEvent().action());
        audit.put("targetId", projection.auditEvent().targetId());
        audit.put("payload", projection.auditEvent().payload());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectionId", projection.projectionId());
        result.put("projectId", projection.projectId());
        result.put("userId", projection.userId());
        result.put("actor", projection.actor());
        result.put("toolsetId", projection.toolsetId());
        result.put("toolName", projection.toolName());
        result.put("executionScope", projection.executionScope());
        result.put("sessionId", projection.sessionId());
        result.put("runId", projection.runId());
        result.put("requestContext", projection.requestContext());
        result.put("checkpointType", projection.checkpointType());
        result.put("checkpointPayload", projection.checkpointPayload());
        result.put("audit", audit);
        return result;
    }

    private CompletionProjection decodeProjection(String json) {
        Object parsed = JSON.parse(json);
        if (!(parsed instanceof Map<?, ?> source)) {
            throw new IllegalStateException("TOOL_COMPLETION_PROJECTION_JSON_INVALID");
        }
        Map<String, Object> values = stringMap(source);
        Map<String, Object> audit = objectMap(values.get("audit"));
        return new CompletionProjection(
                text(values.get("projectionId")),
                text(values.get("projectId")),
                text(values.get("userId")),
                text(values.get("actor")),
                text(values.get("toolsetId")),
                text(values.get("toolName")),
                text(values.get("executionScope")),
                text(values.get("sessionId")),
                text(values.get("runId")),
                objectMap(values.get("requestContext")),
                text(values.get("checkpointType")),
                objectMap(values.get("checkpointPayload")),
                new cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort.ToolExecutionAuditEvent(
                        text(audit.get("projectId")),
                        text(audit.get("action")),
                        text(audit.get("targetId")),
                        audit.get("payload")));
    }

    private String projectionColumn(ProjectionChannel channel) {
        return switch (channel) {
            case CHECKPOINT -> "checkpoint_status";
            case AUDIT -> "audit_status";
        };
    }

    private boolean pending(String status) {
        return PROJECTION_PENDING.equals(status) || PROJECTION_FAILED.equals(status);
    }

    private Reservation decideExisting(
            LedgerRow row,
            ReserveCommand command,
            boolean allowTakeover) {
        if (STATUS_SUCCEEDED.equals(row.status()) || STATUS_BLOCKED.equals(row.status())) {
            return Reservation.reuse(
                    row.fencingToken(),
                    row.allowed(),
                    row.decision(),
                    row.recorded(),
                    row.payload());
        }
        if (STATUS_REVIEW_REQUIRED.equals(row.status())) {
            return Reservation.rejected(
                    Disposition.REVIEW_REQUIRED,
                    first(row.errorCode(), "TOOL_EXECUTION_REVIEW_REQUIRED"));
        }
        boolean leaseExpired = row.leaseExpiresAt() == null
                || !row.leaseExpiresAt().isAfter(command.reservedAt());
        if (STATUS_RUNNING.equals(row.status()) && leaseExpired && row.sideEffecting()) {
            int quarantined = requireJdbc().update("""
                            UPDATE ai_ops_tool_execution_ledger
                               SET status=?, error_code='TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED',
                                   error_message='Side-effecting execution owner lease expired; authoritative receipt reconciliation required',
                                   owner_token='', lease_expires_at=NULL, updated_at=?
                             WHERE idempotency_key=? AND fencing_token=? AND status=?
                               AND side_effecting=1
                               AND (lease_expires_at IS NULL OR lease_expires_at<=?)
                            """,
                    STATUS_REVIEW_REQUIRED,
                    timestamp(command.reservedAt()),
                    command.idempotencyKey(),
                    row.fencingToken(),
                    STATUS_RUNNING,
                    timestamp(command.reservedAt()));
            if (quarantined == 1) {
                return Reservation.rejected(
                        Disposition.REVIEW_REQUIRED,
                        "TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED");
            }
            return decideExisting(requireRow(command.idempotencyKey()), command, false);
        }
        boolean takeoverEligible = STATUS_FAILED_RETRYABLE.equals(row.status())
                || (STATUS_RUNNING.equals(row.status()) && leaseExpired && !row.sideEffecting());
        if (allowTakeover && takeoverEligible) {
            int updated = requireJdbc().update("""
                            UPDATE ai_ops_tool_execution_ledger
                               SET status=?, owner_token=?, fencing_token=fencing_token+1,
                                   lease_expires_at=?, error_code='', error_message='', updated_at=?
                             WHERE idempotency_key=? AND fencing_token=?
                               AND (status=? OR (status=? AND (lease_expires_at IS NULL OR lease_expires_at<=?)))
                            """,
                    STATUS_RUNNING,
                    command.ownerToken(),
                    timestamp(command.leaseExpiresAt()),
                    timestamp(command.reservedAt()),
                    command.idempotencyKey(),
                    row.fencingToken(),
                    STATUS_FAILED_RETRYABLE,
                    STATUS_RUNNING,
                    timestamp(command.reservedAt()));
            if (updated == 1) return Reservation.execute(row.fencingToken() + 1L);
            return decideExisting(requireRow(command.idempotencyKey()), command, false);
        }
        if (STATUS_RUNNING.equals(row.status())) {
            return Reservation.rejected(Disposition.IN_PROGRESS, "TOOL_EXECUTION_ALREADY_RUNNING");
        }
        return Reservation.rejected(Disposition.REVIEW_REQUIRED, "TOOL_EXECUTION_STATE_REVIEW_REQUIRED");
    }

    private LedgerRow requireRow(String idempotencyKey) {
        List<LedgerRow> rows = requireJdbc().query("""
                        SELECT idempotency_key, input_hash, target_hash, side_effecting, reconciliation_json,
                               status, owner_token, fencing_token, lease_expires_at, allowed, decision_code,
                               result_id, evidence_id, preview_text, output_hash, truncated, full_output_ref,
                               recorded_input_hash, duration_ms, payload_json, error_code, error_message
                          FROM ai_ops_tool_execution_ledger
                         WHERE idempotency_key=?
                        """,
                (rs, rowNum) -> row(rs), idempotencyKey);
        if (rows.size() != 1) {
            throw new IllegalStateException("TOOL_EXECUTION_IDEMPOTENCY_LEDGER_ROW_MISSING");
        }
        return rows.get(0);
    }

    private LedgerRow row(ResultSet rs) throws SQLException {
        Timestamp lease = rs.getTimestamp("lease_expires_at");
        return new LedgerRow(
                rs.getString("idempotency_key"),
                rs.getString("input_hash"),
                rs.getString("target_hash"),
                rs.getBoolean("side_effecting"),
                rs.getString("reconciliation_json"),
                rs.getString("status"),
                rs.getString("owner_token"),
                rs.getLong("fencing_token"),
                lease == null ? null : lease.toInstant(),
                rs.getBoolean("allowed"),
                rs.getString("decision_code"),
                rs.getString("result_id"),
                rs.getString("evidence_id"),
                rs.getString("preview_text"),
                rs.getString("output_hash"),
                rs.getBoolean("truncated"),
                rs.getString("full_output_ref"),
                rs.getString("recorded_input_hash"),
                rs.getLong("duration_ms"),
                rs.getString("payload_json"),
                rs.getString("error_code"),
                rs.getString("error_message"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Object value = JSON.parse(json);
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        return Map.copyOf(stringMap(source));
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        return Map.copyOf(stringMap(source));
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object parsed = JSON.parse(json);
            return objectMap(parsed);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private void quarantineExpiredSideEffects(String projectId, String runId) {
        requireJdbc().update("""
                UPDATE ai_ops_tool_execution_ledger
                   SET status=?, error_code='TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED',
                       error_message='Side-effecting execution owner lease expired; authoritative receipt reconciliation required',
                       owner_token='', lease_expires_at=NULL, updated_at=CURRENT_TIMESTAMP(6)
                 WHERE project_id=? AND run_id=? AND status=? AND side_effecting=1
                   AND (lease_expires_at IS NULL OR lease_expires_at<=CURRENT_TIMESTAMP(6))
                """, STATUS_REVIEW_REQUIRED, projectId, runId, STATUS_RUNNING);
    }

    private void quarantineExpiredSideEffects() {
        requireJdbc().update("""
                UPDATE ai_ops_tool_execution_ledger
                   SET status=?, error_code='TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED',
                       error_message='Side-effecting execution owner lease expired; authoritative receipt reconciliation required',
                       owner_token='', lease_expires_at=NULL, updated_at=CURRENT_TIMESTAMP(6)
                 WHERE status=? AND side_effecting=1
                   AND (lease_expires_at IS NULL OR lease_expires_at<=CURRENT_TIMESTAMP(6))
                """, STATUS_REVIEW_REQUIRED, STATUS_RUNNING);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private void ensureLedgerColumn(String columnName, String definition) {
        if (ledgerColumnExists(columnName)) return;
        try {
            requireJdbc().execute(
                    "ALTER TABLE ai_ops_tool_execution_ledger ADD COLUMN "
                            + columnName + " " + definition);
        } catch (DataAccessException error) {
            if (!ledgerColumnExists(columnName)) throw error;
        }
    }

    private boolean ledgerColumnExists(String columnName) {
        Integer count = requireJdbc().queryForObject("""
                        SELECT COUNT(*)
                          FROM information_schema.columns
                         WHERE table_schema=DATABASE()
                           AND table_name='ai_ops_tool_execution_ledger'
                           AND column_name=?
                        """,
                Integer.class,
                columnName);
        return count != null && count > 0;
    }

    private void ensureProjectionColumn(String columnName, String definition) {
        if (projectionColumnExists(columnName)) return;
        try {
            requireJdbc().execute(
                    "ALTER TABLE ai_ops_tool_execution_completion_projection ADD COLUMN "
                            + columnName + " " + definition);
        } catch (DataAccessException error) {
            if (!projectionColumnExists(columnName)) throw error;
        }
    }

    private boolean projectionColumnExists(String columnName) {
        Integer count = requireJdbc().queryForObject("""
                        SELECT COUNT(*)
                          FROM information_schema.columns
                         WHERE table_schema=DATABASE()
                           AND table_name='ai_ops_tool_execution_completion_projection'
                           AND column_name=?
                        """,
                Integer.class,
                columnName);
        return count != null && count > 0;
    }

    private void ensureProjectionIndex() {
        Integer count = requireJdbc().queryForObject("""
                        SELECT COUNT(*)
                          FROM information_schema.statistics
                         WHERE table_schema=DATABASE()
                           AND table_name='ai_ops_tool_execution_completion_projection'
                           AND index_name='idx_tool_completion_claim'
                        """,
                Integer.class);
        if (count != null && count > 0) return;
        try {
            requireJdbc().execute("""
                    ALTER TABLE ai_ops_tool_execution_completion_projection
                    ADD KEY idx_tool_completion_claim (next_attempt_at, lease_expires_at, id)
                    """);
        } catch (DataAccessException error) {
            Integer after = requireJdbc().queryForObject("""
                            SELECT COUNT(*)
                              FROM information_schema.statistics
                             WHERE table_schema=DATABASE()
                               AND table_name='ai_ops_tool_execution_completion_projection'
                               AND index_name='idx_tool_completion_claim'
                            """,
                    Integer.class);
            if (after == null || after == 0) throw error;
        }
    }

    private JdbcTemplate requireJdbc() {
        if (jdbc == null) {
            throw new IllegalStateException("TOOL_EXECUTION_IDEMPOTENCY_STORE_UNAVAILABLE");
        }
        return jdbc;
    }

    private String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record LedgerRow(
            String idempotencyKey,
            String inputHash,
            String targetHash,
            boolean sideEffecting,
            String reconciliationJson,
            String status,
            String ownerToken,
            long fencingToken,
            Instant leaseExpiresAt,
            boolean allowed,
            String decision,
            String resultId,
            String evidenceId,
            String preview,
            String outputHash,
            boolean truncated,
            String fullOutputRef,
            String recordedInputHash,
            long durationMs,
            String payloadJson,
            String errorCode,
            String errorMessage) {

        private ToolExecutionRecordedResult recorded() {
            if (resultId == null || resultId.isBlank()) {
                throw new IllegalStateException("TOOL_EXECUTION_IDEMPOTENCY_RESULT_INCOMPLETE");
            }
            return new ToolExecutionRecordedResult(
                    resultId,
                    evidenceId,
                    preview,
                    outputHash,
                    truncated,
                    fullOutputRef,
                    recordedInputHash,
                    durationMs);
        }

        private Map<String, Object> payload() {
            if (payloadJson == null || payloadJson.isBlank()) return Map.of();
            Object value = JSON.parse(payloadJson);
            if (!(value instanceof Map<?, ?> source)) return Map.of();
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return Map.copyOf(result);
        }
    }
}
