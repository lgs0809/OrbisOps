package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingJournalPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageLandingRuntimeResult;
import cn.lgs.orbisops.application.changepackage.LandingOperationFact;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JDBC adapter for the operation facts required by the Landing process manager. */
@Repository
public class JdbcChangePackageLandingJournalAdapter implements ChangePackageLandingJournalPort {

    private static final String FACT_NONE = "NONE";
    private static final String FACT_UNKNOWN = "UNKNOWN";
    private static final String FACT_COMPLETED = "COMPLETED";
    private static final String STATUS_PENDING = "PENDING";

    private final JdbcTemplate jdbcTemplate;

    public JdbcChangePackageLandingJournalAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void initialize(String landingRunId, ChangePackageLandingPlan plan) {
        JdbcTemplate template = requireTemplate();
        for (ChangePackageLandingOperation operation : plan.operations()) {
            String executionKey = executionKey(plan, operation);
            String operationJson = JSON.toJSONString(operation.raw());
            List<Map<String, Object>> prior = template.queryForList("""
                    SELECT landing_run_id, package_id, project_id, approved_version,
                           approved_package_hash, operation_id, operation_hash, fact_status, status
                    FROM ai_ops_change_package_landing_operation_run
                    WHERE execution_key=?
                    LIMIT 1
                    """, executionKey);
            if (!prior.isEmpty()) {
                Map<String, Object> priorRow = prior.get(0);
                assertStableIdentity(priorRow, plan, operation);
                String factStatus = text(priorRow.get("fact_status"));
                if (FACT_UNKNOWN.equals(factStatus) || FACT_COMPLETED.equals(factStatus)) {
                    throw new IllegalStateException("LANDING_OPERATION_RECONCILIATION_REQUIRED：operation="
                            + operation.operationId() + " fact=" + factStatus);
                }
                String priorLandingRunId = text(priorRow.get("landing_run_id"));
                if (!landingRunId.equals(priorLandingRunId)
                        && !priorLandingRunId.isBlank()
                        && FACT_NONE.equals(factStatus)
                        && STATUS_PENDING.equals(text(priorRow.get("status")))) {
                    rebindProvenUnexecutedAttempt(template, landingRunId, plan, operation,
                            executionKey, operationJson, priorLandingRunId);
                }
                continue;
            }
            template.update("""
                            INSERT INTO ai_ops_change_package_landing_operation_run
                            (operation_run_id, landing_run_id, package_id, project_id, approved_version,
                             approved_package_hash, operation_id, operation_hash, execution_key, adapter_type,
                             toolset_id, tool_name, resource_key, effect_type, stage, status, fact_status,
                             dispatch_attempts, state_version, fencing_token, request_hash, operation_snapshot_json,
                             reason_code, result_json, started_at, finished_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED_LANDING',
                                    'PENDING', 'NONE', 0, 0, 0, ?, ?, '', ?, NULL, NULL)
                            """,
                    "lro-" + UUID.randomUUID(), landingRunId, plan.packageId(), plan.projectId(),
                    plan.approvedVersion(), plan.approvedPackageHash(), operation.operationId(),
                    operation.operationHash(), executionKey, operation.adapterType(), operation.toolsetId(),
                    operation.toolName(), operation.resourceKey(), normalizeEffectType(operation.effectType()),
                    sha256(operationJson), operationJson, operationJson);
        }
    }

    /**
     * The journal has one canonical row per stable execution identity because that identity is
     * also the idempotency key sent to the target. A retry is a new Landing attempt, not a new
     * remote execution. Only a process-manager proof of {@code fact_status=NONE,status=PENDING}
     * permits rotating the attempt-owned columns to the fresh LandingRun. UNKNOWN/COMPLETED rows
     * stay immutable until reconciliation, so an uncertain or already-applied write can never be
     * hidden by a retry. This keeps the operation facts and the UI projection attached to the
     * attempt that actually ran while preserving the target's once-only execution identity.
     */
    private void rebindProvenUnexecutedAttempt(
            JdbcTemplate template,
            String landingRunId,
            ChangePackageLandingPlan plan,
            ChangePackageLandingOperation operation,
            String executionKey,
            String operationJson,
            String priorLandingRunId) {
        int rebound = template.update("""
                UPDATE ai_ops_change_package_landing_operation_run
                   SET operation_run_id=?, landing_run_id=?, status='PENDING', fact_status='NONE',
                       dispatch_attempts=0, state_version=0, fencing_token=0, worker_id='',
                       lease_expires_at=NULL, claimed_at=NULL, remote_request_id='',
                       remote_result_id='', dispatched_at=NULL, acknowledged_at=NULL,
                       verified_at=NULL, unknown_reason_code='', result_id='', output_hash='',
                       reason_code='', precondition_result_json=NULL, execution_result_json=NULL,
                       post_check_result_json=NULL, rollback_status='', rollback_result_json=NULL,
                       rollback_at=NULL, result_json=?, started_at=CURRENT_TIMESTAMP,
                       finished_at=NULL
                 WHERE execution_key=? AND landing_run_id=?
                   AND package_id=? AND project_id=? AND approved_version=?
                   AND approved_package_hash=? AND operation_id=? AND operation_hash=?
                   AND fact_status='NONE' AND status='PENDING'
                """,
                "lro-" + UUID.randomUUID(), landingRunId, operationJson,
                executionKey, priorLandingRunId, plan.packageId(), plan.projectId(),
                plan.approvedVersion(), plan.approvedPackageHash(), operation.operationId(),
                operation.operationHash());
        if (rebound != 1) {
            throw new IllegalStateException("LANDING_OPERATION_REBIND_CONFLICT：operation="
                    + operation.operationId());
        }
    }

    private void assertStableIdentity(
            Map<String, Object> prior,
            ChangePackageLandingPlan plan,
            ChangePackageLandingOperation operation) {
        boolean same = plan.packageId().equals(text(prior.get("package_id")))
                && plan.projectId().equals(text(prior.get("project_id")))
                && plan.approvedVersion() == intValue(prior.get("approved_version"))
                && plan.approvedPackageHash().equals(text(prior.get("approved_package_hash")))
                && operation.operationId().equals(text(prior.get("operation_id")))
                && operation.operationHash().equals(text(prior.get("operation_hash")));
        if (!same) {
            throw new IllegalStateException("LANDING_OPERATION_IDENTITY_CONFLICT：operation="
                    + operation.operationId());
        }
    }

    @Override
    public void completeFromRuntimeResult(String landingRunId,
                                          ChangePackageLandingPlan plan,
                                          ChangePackageLandingRuntimeResult result) {
        if (result == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_RUNTIME_RESULT_REQUIRED");
        }
        if (result.status() == cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.NEEDS_REPLAN) {
            markAllUnexecutedBlocked(landingRunId,
                    result.reasonCode().isBlank() ? "LANDING_BLOCKED" : result.reasonCode(),
                    result.payload());
        }
    }

    @Override
    public List<LandingOperationFact> operationFacts(String landingRunId) {
        return requireTemplate().queryForList("""
                        SELECT operation_id, status, fact_status, reason_code, result_id, output_hash
                        FROM ai_ops_change_package_landing_operation_run
                        WHERE landing_run_id=?
                        ORDER BY id ASC
                        """, landingRunId).stream()
                .map(this::operationFact)
                .toList();
    }

    private LandingOperationFact operationFact(Map<String, Object> row) {
        return new LandingOperationFact(
                text(row.get("operation_id")),
                LandingOperationFact.FactStatus.from(text(row.get("fact_status"))),
                LandingOperationFact.ExecutionStatus.from(text(row.get("status"))),
                text(row.get("reason_code")),
                text(row.get("result_id")),
                text(row.get("output_hash")),
                row);
    }

    @Override
    public void markAllUnexecutedBlocked(String landingRunId, String reasonCode, Object result) {
        requireTemplate().update("""
                        UPDATE ai_ops_change_package_landing_operation_run
                        SET status='BLOCKED', fact_status='NONE', reason_code=?, result_json=?,
                            finished_at=CURRENT_TIMESTAMP
                        WHERE landing_run_id=? AND fact_status='NONE'
                          AND status IN ('PENDING','PRECONDITION_CHECKING')
                        """, text(reasonCode), JSON.toJSONString(result == null ? Map.of() : result), landingRunId);
    }

    private String executionKey(ChangePackageLandingPlan plan, ChangePackageLandingOperation operation) {
        return sha256(plan.projectId() + "\n" + plan.packageId() + "\n" + plan.approvedVersion() + "\n"
                + plan.approvedPackageHash() + "\n" + operation.operationId() + "\n" + operation.operationHash());
    }

    private String normalizeEffectType(String value) {
        String normalized = text(value).toUpperCase(java.util.Locale.ROOT);
        return "MUTATE_TEMP_RESOURCE".equals(normalized) ? "MUTATE_EPHEMERAL" : normalized;
    }

    private JdbcTemplate requireTemplate() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_JOURNAL_STORE_UNAVAILABLE");
        }
        return jdbcTemplate;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_JOURNAL_HASH_FAILED", error);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
    }
}
