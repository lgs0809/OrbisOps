package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.LandingOperationJournalPort;
import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;
import cn.lgs.orbisops.application.changepackage.LandingOperationPayload;
import cn.lgs.orbisops.application.changepackage.LandingOperationRecoveryCandidate;
import cn.lgs.orbisops.application.changepackage.LandingOperationRecoveryClaim;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC reader/CAS adapter used only for historical UNKNOWN Landing reconciliation. */
@Repository
public class JdbcLandingOperationJournalAdapter implements LandingOperationJournalPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLandingOperationJournalAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LandingOperationRecoveryCandidate> expiredUnknown(int limit) {
        return jdbcTemplate.query("""
                        SELECT operation_run_id, landing_run_id, package_id, project_id, approved_version,
                               approved_package_hash, operation_id, operation_hash, execution_key, adapter_type,
                               toolset_id, tool_name, resource_key, effect_type, state_version, fencing_token,
                               operation_snapshot_json
                        FROM ai_ops_change_package_landing_operation_run
                        WHERE fact_status='UNKNOWN'
                          AND status IN ('EXECUTING','POST_CHECKING','FAILED','BLOCKED','RECONCILING')
                          AND (lease_expires_at IS NULL OR lease_expires_at<CURRENT_TIMESTAMP)
                        ORDER BY create_time ASC, id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> recoveryCandidate(Map.ofEntries(
                        Map.entry("operation_run_id", text(rs.getString("operation_run_id"))),
                        Map.entry("landing_run_id", text(rs.getString("landing_run_id"))),
                        Map.entry("package_id", text(rs.getString("package_id"))),
                        Map.entry("project_id", text(rs.getString("project_id"))),
                        Map.entry("approved_version", rs.getInt("approved_version")),
                        Map.entry("approved_package_hash", text(rs.getString("approved_package_hash"))),
                        Map.entry("operation_id", text(rs.getString("operation_id"))),
                        Map.entry("operation_hash", text(rs.getString("operation_hash"))),
                        Map.entry("execution_key", text(rs.getString("execution_key"))),
                        Map.entry("adapter_type", text(rs.getString("adapter_type"))),
                        Map.entry("toolset_id", text(rs.getString("toolset_id"))),
                        Map.entry("tool_name", text(rs.getString("tool_name"))),
                        Map.entry("resource_key", text(rs.getString("resource_key"))),
                        Map.entry("effect_type", text(rs.getString("effect_type"))),
                        Map.entry("state_version", rs.getLong("state_version")),
                        Map.entry("fencing_token", rs.getLong("fencing_token")),
                        Map.entry("operation_snapshot_json", text(rs.getString("operation_snapshot_json"))))),
                Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public Optional<LandingOperationRecoveryCandidate> lockRecoveryCandidate(String operationRunId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT operation_run_id, landing_run_id, package_id, project_id, approved_version,
                       approved_package_hash, operation_id, operation_hash, execution_key, adapter_type,
                       toolset_id, tool_name, resource_key, effect_type, state_version, fencing_token,
                       operation_snapshot_json
                FROM ai_ops_change_package_landing_operation_run
                WHERE operation_run_id=? AND fact_status='UNKNOWN'
                  AND (lease_expires_at IS NULL OR lease_expires_at<CURRENT_TIMESTAMP)
                FOR UPDATE
                """, operationRunId);
        return rows.size() == 1
                ? Optional.of(recoveryCandidate(rows.get(0)))
                : Optional.empty();
    }

    @Override
    public boolean claimRecovery(
            String operationRunId,
            long previousStateVersion,
            long stateVersion,
            String workerId,
            LocalDateTime leaseDeadline) {
        return jdbcTemplate.update("""
                        UPDATE ai_ops_change_package_landing_operation_run
                        SET status='RECONCILING', worker_id=?, lease_expires_at=?, state_version=?
                        WHERE operation_run_id=? AND fact_status='UNKNOWN' AND state_version=?
                        """,
                workerId,
                leaseDeadline,
                stateVersion,
                operationRunId,
                previousStateVersion) == 1;
    }

    @Override
    public boolean completeRecovery(
            LandingOperationRecoveryClaim claim,
            boolean succeeded,
            String reasonCode,
            LandingOperationPayload payload) {
        return jdbcTemplate.update("""
                        UPDATE ai_ops_change_package_landing_operation_run
                        SET status=?, fact_status='COMPLETED', reason_code=?, unknown_reason_code='',
                            result_id=?, output_hash=?, result_json=?, acknowledged_at=CURRENT_TIMESTAMP,
                            verified_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE verified_at END,
                            worker_id='', lease_expires_at=NULL, finished_at=CURRENT_TIMESTAMP,
                            state_version=state_version+1
                        WHERE operation_run_id=? AND fact_status='UNKNOWN'
                          AND state_version=? AND worker_id=?
                        """,
                succeeded ? "SUCCEEDED" : "FAILED",
                reasonCode,
                payload.resultId(),
                payload.outputHash(),
                json(payload.raw()),
                succeeded,
                claim.candidate().operationRunId(),
                claim.stateVersion(),
                claim.workerId()) == 1;
    }

    @Override
    public List<String> operationIdsForTool(
            String landingRunId,
            String toolsetId,
            String toolName) {
        String normalizedToolset = text(toolsetId).startsWith("mcp.")
                ? text(toolsetId).substring("mcp.".length())
                : text(toolsetId);
        return jdbcTemplate.queryForList("""
                        SELECT operation_id
                          FROM ai_ops_change_package_landing_operation_run
                         WHERE landing_run_id=? AND tool_name=?
                           AND (toolset_id=? OR toolset_id=?)
                         ORDER BY id ASC
                        """,
                String.class,
                landingRunId,
                toolName,
                toolsetId,
                normalizedToolset).stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public List<LandingOperationExecutionBinding> operationExecutionBindings(String landingRunId) {
        return jdbcTemplate.query("""
                        SELECT operation_id, execution_key, toolset_id, tool_name, resource_key
                          FROM ai_ops_change_package_landing_operation_run
                         WHERE landing_run_id=?
                         ORDER BY id ASC
                        """,
                (rs, rowNum) -> new LandingOperationExecutionBinding(
                        rs.getString("operation_id"),
                        rs.getString("execution_key"),
                        rs.getString("toolset_id"),
                        rs.getString("tool_name"),
                        rs.getString("resource_key")),
                landingRunId);
    }

    @Override
    public boolean completeFromToolExecution(
            String landingRunId,
            String operationId,
            String toolName,
            String executionKey,
            LandingOperationPayload payload) {
        LandingOperationPayload safe = payload == null
                ? new LandingOperationPayload(Map.of(), "", "", "")
                : payload;
        int updated = jdbcTemplate.update("""
                        UPDATE ai_ops_change_package_landing_operation_run
                           SET status='SUCCEEDED', fact_status='COMPLETED',
                               reason_code='TOOL_EXECUTION_AUTHORITATIVE_COMPLETION', unknown_reason_code='',
                               result_id=?, output_hash=?, result_json=?, acknowledged_at=CURRENT_TIMESTAMP,
                               worker_id='', lease_expires_at=NULL,
                               finished_at=CURRENT_TIMESTAMP, state_version=state_version+1
                         WHERE landing_run_id=? AND operation_id=? AND tool_name=?
                           AND execution_key=?
                           AND fact_status IN ('NONE','UNKNOWN')
                           AND status IN ('PENDING','BLOCKED','RECONCILING')
                        """,
                safe.resultId(),
                safe.outputHash(),
                json(safe.raw()),
                landingRunId,
                operationId,
                toolName,
                executionKey);
        if (updated == 1) return true;
        if (updated > 1) {
            throw new IllegalStateException("LANDING_TOOL_EXECUTION_PROJECTION_NOT_UNIQUE:" + operationId);
        }
        Integer existing = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                          FROM ai_ops_change_package_landing_operation_run
                         WHERE landing_run_id=? AND operation_id=? AND tool_name=?
                           AND execution_key=? AND fact_status='COMPLETED' AND status='SUCCEEDED'
                           AND result_id=? AND output_hash=?
                        """, Integer.class,
                landingRunId, operationId, toolName, executionKey,
                safe.resultId(), safe.outputHash());
        return existing != null && existing == 1;
    }

    @Override
    public boolean leaveRecoveryUnknown(
            LandingOperationRecoveryClaim claim,
            String reasonCode,
            LandingOperationPayload payload) {
        return jdbcTemplate.update("""
                        UPDATE ai_ops_change_package_landing_operation_run
                        SET status='BLOCKED', reason_code=?, unknown_reason_code=?, result_json=?,
                            worker_id='', lease_expires_at=NULL, finished_at=CURRENT_TIMESTAMP,
                            state_version=state_version+1
                        WHERE operation_run_id=? AND fact_status='UNKNOWN'
                          AND state_version=? AND worker_id=?
                        """,
                reasonCode,
                reasonCode,
                json(payload.raw()),
                claim.candidate().operationRunId(),
                claim.stateVersion(),
                claim.workerId()) == 1;
    }

    private LandingOperationRecoveryCandidate recoveryCandidate(Map<String, Object> row) {
        Map<String, Object> operation = parseObject(text(row.get("operation_snapshot_json")));
        return new LandingOperationRecoveryCandidate(
                text(row.get("operation_run_id")),
                text(row.get("landing_run_id")),
                text(row.get("package_id")),
                text(row.get("project_id")),
                number(row.get("approved_version")),
                text(row.get("approved_package_hash")),
                text(row.get("operation_id")),
                text(row.get("operation_hash")),
                text(row.get("execution_key")),
                text(row.get("adapter_type")),
                text(row.get("toolset_id")),
                text(row.get("tool_name")),
                text(row.get("resource_key")),
                text(row.get("effect_type")),
                longValue(row.get("state_version")),
                longValue(row.get("fencing_token")),
                operation);
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank() || !json.startsWith("{")) return Map.of();
        try {
            Map<String, Object> parsed = JSON.parseObject(json);
            return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private String json(Object value) {
        return JSON.toJSONString(value == null ? Map.of() : value);
    }

    private int number(Object value) {
        return (int) longValue(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
