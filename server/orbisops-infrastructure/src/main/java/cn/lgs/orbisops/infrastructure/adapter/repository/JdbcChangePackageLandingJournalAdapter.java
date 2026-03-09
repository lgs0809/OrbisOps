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
            List<Map<String, Object>> prior = template.queryForList("""
                    SELECT fact_status, status, landing_run_id
                    FROM ai_ops_change_package_landing_operation_run
                    WHERE execution_key=?
                    LIMIT 1
                    """, executionKey);
            if (!prior.isEmpty()) {
                String factStatus = text(prior.get(0).get("fact_status"));
                if (FACT_UNKNOWN.equals(factStatus) || FACT_COMPLETED.equals(factStatus)) {
                    throw new IllegalStateException("LANDING_OPERATION_RECONCILIATION_REQUIRED：operation="
                            + operation.operationId() + " fact=" + factStatus);
                }
                continue;
            }
            String operationJson = JSON.toJSONString(operation.raw());
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
}
