package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRun;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRunPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRunStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcChangePackageLandingRunAdapter implements ChangePackageLandingRunPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChangePackageLandingRunAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public Optional<ChangePackageLandingRun> findByIdempotencyKey(String idempotencyKey) {
        return template().queryForList("""
                SELECT * FROM ai_ops_change_package_landing_run
                WHERE idempotency_key=?
                ORDER BY id DESC
                LIMIT 1
                """, required(idempotencyKey)).stream().findFirst().map(this::map);
    }

    @Override
    public Optional<ChangePackageLandingRun> find(String runId) {
        return template().queryForList("""
                SELECT * FROM ai_ops_change_package_landing_run
                WHERE run_id=?
                LIMIT 1
                """, required(runId)).stream().findFirst().map(this::map);
    }

    @Override
    public String executionActor(String runId) {
        return template().queryForObject("SELECT actor FROM ai_ops_change_package_landing_run WHERE run_id=?",
                String.class, required(runId));
    }

    @Override
    public List<ChangePackageLandingRun> findStrandedTerminalRuns(int limit) {
        return template().queryForList("""
                SELECT r.*
                FROM ai_ops_change_package_landing_run r
                JOIN ai_ops_change_package p
                  ON p.package_id=r.package_id
                 AND p.landing_run_id=r.run_id
                WHERE p.status='LANDING_RUNNING'
                  AND r.status IN ('SUCCEEDED','FAILED','NEEDS_REPLAN')
                  AND p.approved_version=r.approved_version
                  AND p.approved_package_hash=r.approved_package_hash
                ORDER BY r.id ASC
                LIMIT ?
                """, Math.max(1, Math.min(limit, 100))).stream().map(this::map).toList();
    }

    @Override
    public List<ChangePackageLandingRun> findLateCompletedFailedRuns(int limit) {
        return template().queryForList("""
                SELECT r.*
                FROM ai_ops_change_package_landing_run r
                JOIN ai_ops_change_package p
                  ON p.package_id=r.package_id
                 AND p.landing_run_id=r.run_id
                WHERE p.status='LANDING_FAILED'
                  AND r.status='FAILED'
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.result_json,'$.reasonCode')),'')
                      <> 'LANDING_INDEPENDENT_VERIFICATION_FAILED'
                  AND p.approved_version=r.approved_version
                  AND p.approved_package_hash=r.approved_package_hash
                  AND EXISTS (
                        SELECT 1
                        FROM ai_ops_change_package_landing_operation_run o
                        WHERE o.landing_run_id=r.run_id)
                  AND NOT EXISTS (
                        SELECT 1
                        FROM ai_ops_change_package_landing_operation_run o
                        WHERE o.landing_run_id=r.run_id
                          AND NOT (o.fact_status='COMPLETED' AND o.status='SUCCEEDED'))
                ORDER BY r.id ASC
                LIMIT ?
                """, Math.max(1, Math.min(limit, 100))).stream().map(this::map).toList();
    }

    @Override
    public void start(String runId,
                      String idempotencyKey,
                      ChangePackageCurrent current,
                      int approvedVersion,
                      String approvedPackageHash,
                      String leaseToken,
                      String actor) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        template().update("""
                        INSERT INTO ai_ops_change_package_landing_run
                        (run_id, package_id, project_id, approved_version, approved_package_hash, idempotency_key,
                         status, actor, lease_token, lease_expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE))
                        """,
                required(runId), current.packageId(), current.projectId(), approvedVersion,
                required(approvedPackageHash), required(idempotencyKey), text(actor), required(leaseToken));
    }

    @Override
    public void complete(String runId, ChangePackageLandingRunStatus status, Object result) {
        int updated = template().update("""
                UPDATE ai_ops_change_package_landing_run
                SET status=?, result_json=?, finished_at=CURRENT_TIMESTAMP, update_time=CURRENT_TIMESTAMP
                WHERE run_id=?
                """, requiredStatus(status).name(),
                JSON.toJSONString(result == null ? Map.of() : result), required(runId));
        if (updated != 1) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_RUN_UPDATE_CONFLICT:" + runId);
        }
    }

    private ChangePackageLandingRun map(Map<String, Object> row) {
        return new ChangePackageLandingRun(
                text(row.get("run_id")),
                text(row.get("package_id")),
                text(row.get("project_id")),
                intValue(row.get("approved_version")),
                text(row.get("approved_package_hash")),
                text(row.get("idempotency_key")),
                ChangePackageLandingRunStatus.from(text(row.get("status"))),
                expired(row.get("lease_expires_at")),
                object(row.get("result_json")));
    }

    private boolean expired(Object value) {
        if (value == null) return true;
        Instant instant = null;
        if (value instanceof Timestamp timestamp) instant = timestamp.toInstant();
        else if (value instanceof Instant parsed) instant = parsed;
        else if (value instanceof LocalDateTime dateTime) instant = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        else {
            try {
                instant = Timestamp.valueOf(String.valueOf(value)).toInstant();
            } catch (RuntimeException ignored) {
                try {
                    instant = Instant.parse(String.valueOf(value));
                } catch (RuntimeException ignoredAgain) {
                    return false;
                }
            }
        }
        return instant.isBefore(Instant.now());
    }

    private Map<String, Object> object(Object raw) {
        if (raw instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        Map<String, Object> parsed = JSON.parseObject(String.valueOf(raw),
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        return parsed == null ? Map.of() : parsed;
    }

    private JdbcTemplate template() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_LANDING_RUN_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private ChangePackageLandingRunStatus requiredStatus(ChangePackageLandingRunStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_RUN_STATUS_REQUIRED");
        }
        return status;
    }

    private String required(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_VALUE_REQUIRED");
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
