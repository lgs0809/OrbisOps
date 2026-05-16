package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.worksession.run.adapter.repository.IWorkSessionRunRepository;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionCheckpoint;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionParticipantRole;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryCandidate;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryDecision;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStart;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcWorkSessionRunRepository implements IWorkSessionRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkSessionRunRepository(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Work Session 持久化需要 mysqlJdbcTemplate");
        }
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public WorkSessionRunClaim claim(
            WorkSessionRunStart start,
            WorkSessionCheckpoint initialCheckpoint) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO ai_ops_agent_run
                  (run_id, project_id, session_id, user_id, agent_id, agent_version, agent_definition_hash,
                   execution_harness, status, current_attempt_id, state_version, fencing_token, worker_id,
                   lease_token, lease_expires_at, cancel_requested, run_manifest_json, run_manifest_hash,
                   request_json, response_json, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', '', 0, 0, '', '', NULL, 0, ?, ?, ?, NULL, NULL, ?, ?)
                """,
                start.runId(), start.projectId(), start.sessionId(), start.actor(),
                start.agentId(), start.agentVersion(), start.agentDefinitionHash(),
                start.executionHarness(), JSON.toJSONString(start.manifest()), start.manifestHash(),
                JSON.toJSONString(start.requestPayload()), timestamp(start.startedAt()), timestamp(start.startedAt()));

        int claimed = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET status='RUNNING', current_attempt_id=?, state_version=state_version+1,
                    fencing_token=fencing_token+1, worker_id=?, lease_token=?,
                    lease_expires_at=?, cancel_requested=0,
                    run_manifest_json=?, run_manifest_hash=?, updated_at=?
                WHERE run_id=? AND project_id=?
                  AND status IN ('PENDING','RECOVERABLE','WAITING_APPROVAL')
                  AND cancel_requested=0
                  AND user_id=? AND session_id=?
                  AND agent_id=? AND agent_version=? AND agent_definition_hash=? AND execution_harness=?
                """,
                start.attemptId(), start.workerId(), start.leaseToken(), timestamp(start.leaseExpiresAt()),
                JSON.toJSONString(start.manifest()), start.manifestHash(), timestamp(start.startedAt()),
                start.runId(), start.projectId(), start.actor(), start.sessionId(),
                start.agentId(), start.agentVersion(), start.agentDefinitionHash(), start.executionHarness());
        if (claimed != 1) {
            throw new IllegalStateException("WORK_SESSION_ALREADY_CLAIMED：runId=" + start.runId());
        }
        WorkSessionRunSnapshot claimedRun = find(start.runId(), start.projectId()).orElseThrow();
        WorkSessionRunClaim claim = new WorkSessionRunClaim(
                start.runId(), start.projectId(), start.attemptId(), start.leaseToken(),
                claimedRun.fencingToken(), claimedRun.stateVersion(), start.manifestHash());
        jdbcTemplate.update("""
                INSERT INTO ai_ops_agent_run_attempt
                  (attempt_id, run_id, project_id, worker_id, lease_token, fencing_token, status,
                   started_at, heartbeat_at, run_manifest_hash)
                VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                claim.attemptId(), claim.runId(), claim.projectId(), start.workerId(),
                claim.leaseToken(), claim.fencingToken(), start.manifestHash());
        snapshotParticipants(start);
        appendCheckpointInternal(claim, initialCheckpoint, "");
        return claim;
    }

    @Override
    public Optional<WorkSessionRunSnapshot> find(String runId, String projectId) {
        return jdbcTemplate.query("""
                        SELECT run_id, project_id, session_id, user_id, agent_id, agent_version,
                               agent_definition_hash, execution_harness, status, current_attempt_id,
                               state_version, fencing_token, worker_id, lease_token, lease_expires_at,
                               cancel_requested, run_manifest_json, run_manifest_hash, request_json,
                               response_json, error_message, created_at, updated_at
                        FROM ai_ops_agent_run WHERE run_id=? AND project_id=? LIMIT 1
                        """,
                this::snapshot, runId, projectId).stream().findFirst();
    }

    @Override
    public Optional<WorkSessionRunSnapshot> findByRunId(String runId) {
        return jdbcTemplate.query("""
                        SELECT run_id, project_id, session_id, user_id, agent_id, agent_version,
                               agent_definition_hash, execution_harness, status, current_attempt_id,
                               state_version, fencing_token, worker_id, lease_token, lease_expires_at,
                               cancel_requested, run_manifest_json, run_manifest_hash, request_json,
                               response_json, error_message, created_at, updated_at
                        FROM ai_ops_agent_run WHERE run_id=? LIMIT 1
                        """,
                this::snapshot, runId).stream().findFirst();
    }

    @Override
    public List<WorkSessionRunSnapshot> findByStatus(WorkSessionRunStatus status, int limit) {
        if (status == null) return List.of();
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return jdbcTemplate.query("""
                        SELECT run_id, project_id, session_id, user_id, agent_id, agent_version,
                               agent_definition_hash, execution_harness, status, current_attempt_id,
                               state_version, fencing_token, worker_id, lease_token, lease_expires_at,
                               cancel_requested, run_manifest_json, run_manifest_hash, request_json,
                               response_json, error_message, created_at, updated_at
                        FROM ai_ops_agent_run
                        WHERE status=?
                        ORDER BY updated_at DESC
                        LIMIT ?
                        """,
                this::snapshot, status.name(), boundedLimit);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean bindManifest(
            WorkSessionRunClaim claim,
            Map<String, Object> manifest,
            String manifestHash,
            WorkSessionCheckpoint checkpoint,
            Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET run_manifest_json=?, run_manifest_hash=?, state_version=state_version+1, updated_at=?
                WHERE run_id=? AND project_id=? AND current_attempt_id=? AND lease_token=?
                  AND fencing_token=? AND status='RUNNING' AND cancel_requested=0
                  AND lease_expires_at>CURRENT_TIMESTAMP(6)
                """,
                JSON.toJSONString(manifest), manifestHash, timestamp(updatedAt),
                claim.runId(), claim.projectId(), claim.attemptId(), claim.leaseToken(), claim.fencingToken());
        if (updated != 1) return false;
        jdbcTemplate.update("""
                UPDATE ai_ops_agent_run_attempt SET run_manifest_hash=?, heartbeat_at=CURRENT_TIMESTAMP
                WHERE attempt_id=? AND run_id=? AND lease_token=? AND fencing_token=?
                """,
                manifestHash, claim.attemptId(), claim.runId(), claim.leaseToken(), claim.fencingToken());
        appendCheckpointInternal(claim, checkpoint, "");
        return true;
    }

    @Override
    public boolean heartbeat(WorkSessionRunClaim claim, Instant leaseExpiresAt, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET lease_expires_at=?, updated_at=?
                WHERE run_id=? AND project_id=? AND current_attempt_id=? AND lease_token=?
                  AND fencing_token=? AND status='RUNNING' AND cancel_requested=0
                  AND lease_expires_at>CURRENT_TIMESTAMP(6)
                """,
                timestamp(leaseExpiresAt), timestamp(updatedAt), claim.runId(), claim.projectId(),
                claim.attemptId(), claim.leaseToken(), claim.fencingToken());
        if (updated != 1) return false;
        jdbcTemplate.update("""
                UPDATE ai_ops_agent_run_attempt SET heartbeat_at=CURRENT_TIMESTAMP
                WHERE attempt_id=? AND run_id=? AND lease_token=? AND fencing_token=? AND status='RUNNING'
                """,
                claim.attemptId(), claim.runId(), claim.leaseToken(), claim.fencingToken());
        return true;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean suspendForApproval(WorkSessionRunClaim claim, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET status='WAITING_APPROVAL', state_version=state_version+1,
                    lease_token='', lease_expires_at=NULL, updated_at=?
                WHERE run_id=? AND project_id=? AND current_attempt_id=? AND lease_token=?
                  AND fencing_token=? AND status='RUNNING' AND cancel_requested=0
                  AND lease_expires_at>CURRENT_TIMESTAMP(6)
                """,
                timestamp(updatedAt), claim.runId(), claim.projectId(), claim.attemptId(),
                claim.leaseToken(), claim.fencingToken());
        if (updated != 1) return false;
        jdbcTemplate.update("""
                UPDATE ai_ops_agent_run_attempt
                SET status='WAITING_APPROVAL', finished_at=CURRENT_TIMESTAMP
                WHERE attempt_id=? AND run_id=? AND lease_token=? AND fencing_token=? AND status='RUNNING'
                """,
                claim.attemptId(), claim.runId(), claim.leaseToken(), claim.fencingToken());
        return true;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public long appendCheckpoint(WorkSessionRunClaim claim, WorkSessionCheckpoint checkpoint) {
        return appendCheckpointInternal(claim, checkpoint, "");
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public long appendCheckpoint(
            WorkSessionRunClaim claim,
            WorkSessionCheckpoint checkpoint,
            String deliveryKey) {
        return appendCheckpointInternal(claim, checkpoint, deliveryKey);
    }

    @Override
    public Optional<WorkSessionCheckpoint> latestCheckpoint(
            String runId,
            String projectId,
            String checkpointTypePrefix) {
        String prefix = checkpointTypePrefix == null ? "" : checkpointTypePrefix.trim();
        return jdbcTemplate.query("""
                        SELECT checkpoint_type, checkpoint_json, checkpoint_hash, created_at
                        FROM ai_ops_agent_run_checkpoint
                        WHERE run_id=? AND project_id=? AND checkpoint_type LIKE ?
                        ORDER BY checkpoint_seq DESC LIMIT 1
                        """,
                (resultSet, rowNum) -> new WorkSessionCheckpoint(
                        resultSet.getString("checkpoint_type"),
                        objectMap(resultSet.getString("checkpoint_json")),
                        resultSet.getString("checkpoint_hash"),
                        requiredInstant(resultSet.getTimestamp("created_at"))),
                runId, projectId, prefix + "%").stream().findFirst();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean finish(
            WorkSessionRunClaim claim,
            WorkSessionRunStatus status,
            Map<String, Object> responsePayload,
            String errorMessage,
            Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET status=?, state_version=state_version+1, lease_token='', lease_expires_at=NULL,
                    response_json=?, error_message=?, updated_at=?
                WHERE run_id=? AND project_id=? AND current_attempt_id=? AND lease_token=?
                  AND fencing_token=? AND status='RUNNING'
                  AND lease_expires_at>CURRENT_TIMESTAMP(6)
                  AND (cancel_requested=0 OR ?='CANCELED')
                """,
                status.name(), responsePayload == null || responsePayload.isEmpty()
                        ? null : cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringifyPreservingOrder(responsePayload), errorMessage, timestamp(updatedAt),
                claim.runId(), claim.projectId(), claim.attemptId(), claim.leaseToken(), claim.fencingToken(), status.name());
        if (updated != 1) return false;
        jdbcTemplate.update("""
                UPDATE ai_ops_agent_run_attempt
                SET status=?, finished_at=CURRENT_TIMESTAMP, error_message=?
                WHERE attempt_id=? AND run_id=? AND lease_token=? AND fencing_token=? AND status='RUNNING'
                """,
                status.name(), errorMessage, claim.attemptId(), claim.runId(),
                claim.leaseToken(), claim.fencingToken());
        return true;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean requestCancel(
            String runId,
            String projectId,
            long expectedVersion,
            String actor,
            String reason,
            Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET cancel_requested=1, cancel_requested_at=CURRENT_TIMESTAMP(3), cancel_requested_by=?,
                    cancel_reason=?, state_version=state_version+1, updated_at=?,
                    status=CASE WHEN status='RUNNING' THEN status ELSE 'CANCELED' END
                WHERE run_id=? AND project_id=? AND state_version=?
                  AND (cancel_requested=0 OR status='RECOVERABLE')
                  AND status IN ('PENDING','RUNNING','WAITING_APPROVAL','RECOVERABLE')
                """,
                actor, reason, timestamp(updatedAt), runId, projectId, expectedVersion);
        if (updated != 1) return false;
        // Suspended runs have no live worker to finish cancellation. Preserve old lost attempts for audit.
        jdbcTemplate.update("""
                UPDATE ai_ops_agent_run_attempt a JOIN ai_ops_agent_run r
                  ON r.run_id=a.run_id AND r.current_attempt_id=a.attempt_id
                SET a.status='CANCELED', a.finished_at=CURRENT_TIMESTAMP(6), a.error_message=?
                WHERE r.run_id=? AND r.project_id=? AND r.status='CANCELED'
                  AND a.status IN ('PENDING','WAITING_APPROVAL')
                """, reason, runId, projectId);
        return true;
    }

    @Override
    public boolean cancelRequested(String runId, String projectId) {
        List<Integer> values = jdbcTemplate.query("""
                SELECT cancel_requested FROM ai_ops_agent_run WHERE run_id=? AND project_id=? LIMIT 1
                """, (resultSet, rowNum) -> resultSet.getInt(1), runId, projectId);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Work Session 不存在或不属于当前项目");
        }
        return values.get(0) == 1;
    }

    @Override
    public Optional<WorkSessionParticipantRole> participantRole(
            String runId,
            String projectId,
            String actor) {
        return jdbcTemplate.queryForList("""
                        SELECT participant_role FROM ai_ops_agent_run_participant
                        WHERE run_id=? AND project_id=? AND participant_user_id=? AND status='ACTIVE' LIMIT 1
                        """,
                String.class, runId, projectId, actor).stream()
                .findFirst()
                .map(WorkSessionParticipantRole::parse);
    }

    @Override
    public List<WorkSessionRecoveryCandidate> findExpiredLeases(int limit, Instant now) {
        List<Map<String, Object>> expired = jdbcTemplate.queryForList("""
                SELECT run_id, project_id, current_attempt_id, state_version, cancel_requested
                FROM ai_ops_agent_run
                WHERE status='RUNNING' AND lease_expires_at IS NOT NULL
                  AND lease_expires_at<?
                ORDER BY lease_expires_at ASC LIMIT ?
                """,
                timestamp(now), Math.max(1, Math.min(limit, 200)));
        List<WorkSessionRecoveryCandidate> result = new ArrayList<>();
        for (Map<String, Object> row : expired) {
            String runId = text(row.get("run_id"));
            String projectId = text(row.get("project_id"));
            List<Map<String, Object>> checkpoints = jdbcTemplate.queryForList("""
                    SELECT checkpoint_seq, checkpoint_type, checkpoint_json
                    FROM ai_ops_agent_run_checkpoint
                    WHERE run_id=? AND checkpoint_type IN ('TOOL_EXECUTION_STARTED','TOOL_EXECUTION_COMPLETED')
                    ORDER BY checkpoint_seq ASC
                    """, runId);
            Map<String, Map<String, Object>> startedByCall = new LinkedHashMap<>();
            Set<String> completedCalls = new LinkedHashSet<>();
            for (Map<String, Object> checkpoint : checkpoints) {
                String type = text(checkpoint.get("checkpoint_type"));
                Map<String, Object> payload = objectMap(text(checkpoint.get("checkpoint_json")));
                String toolCallId = text(payload.get("toolCallId"));
                if (toolCallId.isBlank()) {
                    toolCallId = "checkpoint:" + number(checkpoint.get("checkpoint_seq"));
                }
                if ("TOOL_EXECUTION_STARTED".equals(type)) {
                    startedByCall.put(toolCallId, payload);
                } else if ("TOOL_EXECUTION_COMPLETED".equals(type)) {
                    completedCalls.add(toolCallId);
                }
            }
            Integer toolResults = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM ai_ops_tool_result WHERE run_id=? AND project_id=?
                    """, Integer.class, runId, projectId);
            int unsafeIncomplete = 0;
            for (Map.Entry<String, Map<String, Object>> started : startedByCall.entrySet()) {
                if (completedCalls.contains(started.getKey())) continue;
                Map<String, Object> payload = started.getValue();
                boolean readOnly = bool(payload.get("readOnly"));
                boolean writesTargetResource = bool(payload.get("writesTargetResource"));
                if (!readOnly || writesTargetResource) unsafeIncomplete++;
            }
            if (startedByCall.isEmpty() && completedCalls.isEmpty()
                    && toolResults != null && toolResults > 0) {
                unsafeIncomplete = 1;
            }
            result.add(new WorkSessionRecoveryCandidate(
                    runId, projectId, text(row.get("current_attempt_id")), number(row.get("state_version")),
                    startedByCall.size(), completedCalls.size(),
                    toolResults == null ? 0 : toolResults,
                    unsafeIncomplete, bool(row.get("cancel_requested"))));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean markRecovery(
            WorkSessionRecoveryCandidate candidate,
            WorkSessionRecoveryDecision decision,
            Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET status=?, state_version=state_version+1, worker_id='', lease_token='', lease_expires_at=NULL,
                    error_message=?, updated_at=?
                WHERE run_id=? AND project_id=? AND status='RUNNING' AND state_version=?
                  AND lease_expires_at<CURRENT_TIMESTAMP(6)
                """,
                decision.status().name(), decision.reasonCode(), timestamp(updatedAt),
                candidate.runId(), candidate.projectId(), candidate.stateVersion());
        if (updated != 1) return false;
        jdbcTemplate.update("""
                UPDATE ai_ops_agent_run_attempt
                SET status='LOST', finished_at=CURRENT_TIMESTAMP, error_message=?
                WHERE attempt_id=? AND run_id=? AND status='RUNNING'
                """,
                decision.reasonCode(), candidate.attemptId(), candidate.runId());
        return true;
    }

    private long appendCheckpointInternal(
            WorkSessionRunClaim claim,
            WorkSessionCheckpoint checkpoint,
            String deliveryKey) {
        String normalizedDeliveryKey = text(deliveryKey);
        if (!normalizedDeliveryKey.isBlank()) {
            Optional<Long> existing = checkpointSequence(claim, normalizedDeliveryKey);
            if (existing.isPresent()) return existing.get();
        }
        int allocated = jdbcTemplate.update("""
                UPDATE ai_ops_agent_run FORCE INDEX (uk_run_id)
                SET next_checkpoint_seq=LAST_INSERT_ID(next_checkpoint_seq + 1), updated_at=?
                WHERE run_id=? AND project_id=? AND current_attempt_id=? AND lease_token=?
                  AND fencing_token=? AND status='RUNNING' AND cancel_requested=0
                  AND lease_expires_at>CURRENT_TIMESTAMP(6)
                """,
                timestamp(checkpoint.createdAt()), claim.runId(), claim.projectId(), claim.attemptId(),
                claim.leaseToken(), claim.fencingToken());
        if (allocated != 1) return 0L;
        Long sequence = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (sequence == null || sequence <= 0L) {
            throw new IllegalStateException("WORK_SESSION_CHECKPOINT_SEQUENCE_INVALID");
        }
        int inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO ai_ops_agent_run_checkpoint
                  (run_id, project_id, attempt_id, checkpoint_seq, checkpoint_type,
                   checkpoint_json, checkpoint_hash, delivery_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                claim.runId(), claim.projectId(), claim.attemptId(), sequence,
                checkpoint.checkpointType(), JSON.toJSONString(checkpoint.payload()),
                checkpoint.payloadHash(), normalizedDeliveryKey.isBlank() ? null : normalizedDeliveryKey,
                timestamp(checkpoint.createdAt()));
        if (inserted == 1) return sequence;
        return checkpointSequence(claim, normalizedDeliveryKey)
                .orElseThrow(() -> new IllegalStateException("WORK_SESSION_CHECKPOINT_DEDUP_LOOKUP_FAILED"));
    }

    private Optional<Long> checkpointSequence(WorkSessionRunClaim claim, String deliveryKey) {
        if (deliveryKey == null || deliveryKey.isBlank()) return Optional.empty();
        return jdbcTemplate.queryForList("""
                        SELECT checkpoint_seq FROM ai_ops_agent_run_checkpoint
                        WHERE run_id=? AND project_id=? AND delivery_key=? LIMIT 1
                        """,
                Long.class, claim.runId(), claim.projectId(), deliveryKey).stream().findFirst();
    }

    private void snapshotParticipants(WorkSessionRunStart start) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_agent_run_participant
                  (run_id,project_id,session_id,participant_user_id,participant_role,status,source)
                VALUES (?,?,?,?, 'OWNER','ACTIVE','RUN_OWNER')
                ON DUPLICATE KEY UPDATE participant_role='OWNER',status='ACTIVE'
                """,
                start.runId(), start.projectId(), start.sessionId(), start.actor());
        jdbcTemplate.update("""
                INSERT INTO ai_ops_agent_run_participant
                  (run_id,project_id,session_id,participant_user_id,participant_role,status,source)
                SELECT ?,?,?,participant_user_id,participant_role,'ACTIVE','SESSION_SNAPSHOT'
                FROM ai_ops_chat_session_participant
                WHERE session_id=? AND project_id=? AND status='ACTIVE'
                ON DUPLICATE KEY UPDATE participant_role=VALUES(participant_role),status='ACTIVE'
                """,
                start.runId(), start.projectId(), start.sessionId(), start.sessionId(), start.projectId());
    }

    private WorkSessionRunSnapshot snapshot(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkSessionRunSnapshot(
                resultSet.getString("run_id"),
                resultSet.getString("project_id"),
                resultSet.getString("session_id"),
                resultSet.getString("user_id"),
                resultSet.getString("agent_id"),
                resultSet.getInt("agent_version"),
                resultSet.getString("agent_definition_hash"),
                resultSet.getString("execution_harness"),
                WorkSessionRunStatus.parse(resultSet.getString("status")),
                resultSet.getString("current_attempt_id"),
                resultSet.getLong("state_version"),
                resultSet.getLong("fencing_token"),
                resultSet.getString("worker_id"),
                resultSet.getString("lease_token"),
                instant(resultSet.getTimestamp("lease_expires_at")),
                resultSet.getInt("cancel_requested") == 1,
                objectMap(resultSet.getString("run_manifest_json")),
                resultSet.getString("run_manifest_hash"),
                objectMap(resultSet.getString("request_json")),
                objectMap(resultSet.getString("response_json")),
                resultSet.getString("error_message"),
                requiredInstant(resultSet.getTimestamp("created_at")),
                requiredInstant(resultSet.getTimestamp("updated_at")));
    }

    private Map<String, Object> objectMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> value = JSON.parseObject(json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return value == null ? Map.of() : new LinkedHashMap<>(value);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Work Session JSON 无法解析", error);
        }
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Instant requiredInstant(Timestamp value) {
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        return "true".equalsIgnoreCase(text(value)) || "1".equals(text(value));
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
