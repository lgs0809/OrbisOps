package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageProductMetricsProjection;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryPort;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentQuery;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcChangePackageQueryAdapter implements ChangePackageQueryPort {

    // The ReAct Landing path records operation start in the execution ledger.
    // Join only the same successful side effect and immutable receipt; never infer
    // a timestamp from package creation, approval, or the time of this query.
    static final String LANDING_OPERATION_QUERY = """
            SELECT o.operation_run_id, o.landing_run_id, o.package_id, o.project_id, o.approved_version,
                   o.approved_package_hash, o.operation_id, o.operation_hash, o.adapter_type, o.toolset_id,
                   o.tool_name, o.resource_key, o.effect_type, o.stage, o.status, o.fact_status, o.reason_code,
                   o.result_id, o.output_hash, o.precondition_result_json, o.execution_result_json,
                   o.post_check_result_json, o.rollback_status, o.rollback_result_json, o.rollback_at,
                   o.result_json, o.started_at, o.finished_at,
                   UNIX_TIMESTAMP(COALESCE(e.created_at, o.started_at)) AS startedEpoch,
                   UNIX_TIMESTAMP(COALESCE(e.updated_at, o.finished_at)) AS finishedEpoch,
                   CASE WHEN e.updated_at IS NOT NULL THEN 'TOOL_EXECUTION_LEDGER_COMPLETED_AT'
                        WHEN o.finished_at IS NOT NULL THEN 'LANDING_OPERATION_FINISHED_AT'
                        ELSE 'MISSING' END AS finishedEpochSource,
                   CASE WHEN e.created_at IS NOT NULL THEN 'TOOL_EXECUTION_LEDGER_CREATED_AT'
                        WHEN o.started_at IS NOT NULL THEN 'LANDING_OPERATION_STARTED_AT'
                        ELSE 'MISSING' END AS startedEpochSource
            FROM ai_ops_change_package_landing_operation_run o
            LEFT JOIN ai_ops_tool_execution_ledger e
              ON e.idempotency_key=o.execution_key AND e.project_id=o.project_id
             AND e.run_id=o.landing_run_id AND e.status='SUCCEEDED' AND e.side_effecting=1
             AND e.result_id=o.result_id AND e.output_hash=o.output_hash
             AND o.result_id<>'' AND o.output_hash<>''
            WHERE o.package_id=? ORDER BY o.id DESC LIMIT ?
            """;

    private static final List<String> SNAPSHOT_PROJECTION_FIELDS = List.of(
            "contextBundleId", "contextBundleHash", "memoryContextRefs", "memoryContextHash",
            "compressedMemorySummary", "memoryInjectionVersion", "usedSkillVersionRefs",
            "usedSkillRefsHash", "toolsetRefs", "toolsetBoundaryHash", "policyRefs", "policyHash",
            "runtimeBoundaryHash", "contextApprovalBoundaryHash", "approvalBoundaryHash",
            "trustedProofRefs", "validationReport", "preparationMethodRef",
            "preparationMethodHash", "preparationMethodSummary");

    private final IChangePackageCurrentRepository currentRepository;
    private final IChangePackageVersionRepository versionRepository;
    private final IChangePackageEventRepository eventRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.approved-landing.enabled:false}")
    private boolean approvedLandingEnabled;

    public JdbcChangePackageQueryAdapter(IChangePackageCurrentRepository currentRepository,
                                         IChangePackageVersionRepository versionRepository,
                                         IChangePackageEventRepository eventRepository,
                                         @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.currentRepository = currentRepository;
        this.versionRepository = versionRepository;
        this.eventRepository = eventRepository;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public Map<String, Object> capabilities() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", currentRepository.available());
        data.put("states", java.util.Arrays.stream(ChangePackageStatus.values()).map(Enum::name).toList());
        data.put("packageTypes", java.util.Arrays.stream(ChangePackageType.values()).map(Enum::name).toList());
        data.put("landingAgentEditable", false);
        data.put("fakeSuccessAllowed", false);
        data.put("approvedLandingEnabled", approvedLandingEnabled);
        return data;
    }

    @Override
    public List<Map<String, Object>> list(ChangePackageListQuery query) {
        if (query == null) throw new IllegalArgumentException("CHANGE_PACKAGE_LIST_QUERY_REQUIRED");
        Map<String, Object> filters = query.filters();
        String status = text(filters.get("status"));
        if (!status.isBlank()) ChangePackageStatus.require(status);
        StringBuilder sql = new StringBuilder("SELECT * FROM ai_ops_change_package WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        appendListEquals(sql, args, "project_id", text(filters.get("projectId")));
        appendListEquals(sql, args, "session_id", text(filters.get("sessionId")));
        appendListEquals(sql, args, "incident_id", text(filters.get("incidentId")));
        appendListEquals(sql, args, "status", status);
        sql.append(" ORDER BY update_time DESC, id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(query.limit(), 500)));
        return template().queryForList(sql.toString(), args.toArray()).stream()
                .map(this::adminCurrentView)
                .toList();
    }

    @Override
    public Map<String, Object> detail(String packageId) {
        ChangePackageCurrent current = current(packageId);
        ChangePackageVersion version = version(packageId, current.version());
        if (!current.packageHash().equals(version.packageHash())) {
            throw new IllegalStateException("ChangePackage 当前版本与版本快照 Hash 不一致："
                    + packageId + "@" + current.version());
        }
        Map<String, Object> snapshot = version.snapshot().toMap();
        if (!current.packageHash().equals(text(snapshot.get("packageHash")))) {
            throw new IllegalStateException("ChangePackage 当前版本快照内容 Hash 不一致："
                    + packageId + "@" + current.version());
        }
        Map<String, Object> result = currentView(current);
        projectSnapshotFields(result, snapshot);
        return result;
    }

    @Override
    public List<Map<String, Object>> versions(String packageId) {
        return versions().findAll(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED")).stream()
                .map(this::versionView).toList();
    }

    @Override
    public List<Map<String, Object>> events(String packageId, int limit) {
        return events().findRecent(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"), limit).stream()
                .map(this::eventView).toList();
    }

    @Override
    public List<Map<String, Object>> landingOperationRuns(String packageId, int limit) {
        String id = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        detail(id);
        return template().queryForList(LANDING_OPERATION_QUERY, id, Math.max(1, Math.min(limit, 500))).stream()
                .map(this::landingRunView)
                .toList();
    }

    @Override
    public Map<String, Object> landingPlan(String packageId) {
        ChangePackageCurrent current = current(packageId);
        if (!current.pointer().approved()) {
            throw new IllegalStateException("ChangePackage 尚未审批，不能生成 Landing Plan");
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("packageId", current.packageId());
        plan.put("approvedVersion", current.pointer().approvedVersion());
        plan.put("approvedPackageHash", current.pointer().approvedPackageHash());
        plan.put("packageType", current.packageType().name());
        plan.put("targetEnvironment", current.state().value(ChangePackageCurrentField.TARGET_ENVIRONMENT));
        plan.put("targetScopeJson", current.state().value(ChangePackageCurrentField.TARGET_SCOPE_JSON));
        plan.put("allowedToolsJson", current.state().value(ChangePackageCurrentField.ALLOWED_TOOLS_JSON));
        plan.put("forbiddenToolsJson", current.state().value(ChangePackageCurrentField.FORBIDDEN_TOOLS_JSON));
        plan.put("allowedLandingAdjustmentsJson",
                current.state().value(ChangePackageCurrentField.ALLOWED_LANDING_ADJUSTMENTS_JSON));
        plan.put("landingAgentEditable", false);
        return plan;
    }

    @Override
    public ChangePackageProductMetricsProjection productMetrics() {
        if (jdbcTemplate == null) return ChangePackageProductMetricsProjection.empty();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                  (SELECT COUNT(1) FROM ai_ops_change_package) AS package_count,
                  (SELECT COUNT(DISTINCT incident_id) FROM ai_ops_change_package
                    WHERE incident_id IS NOT NULL AND incident_id<>'') AS incident_linked_count,
                  (SELECT COUNT(DISTINCT package_id) FROM ai_ops_change_package_event
                    WHERE event_type='PACKAGE_APPROVED') AS approved_count,
                  (SELECT COUNT(DISTINCT package_id) FROM ai_ops_change_package_event
                    WHERE event_type='LANDING_SUCCEEDED') AS landed_count,
                  (SELECT COUNT(DISTINCT package_id) FROM ai_ops_change_package_event
                    WHERE event_type='LANDING_FAILED') AS landing_failed_count,
                  (SELECT COUNT(DISTINCT package_id) FROM ai_ops_change_package_event
                    WHERE event_type='LANDING_NEEDS_REPLAN') AS needs_replan_count,
                  (SELECT COUNT(DISTINCT package_id) FROM ai_ops_change_package_event
                    WHERE event_type='PACKAGE_REJECTED') AS rejected_count
                """);
        return new ChangePackageProductMetricsProjection(
                number(row.get("package_count")),
                number(row.get("incident_linked_count")),
                number(row.get("approved_count")),
                number(row.get("landed_count")),
                number(row.get("landing_failed_count")),
                number(row.get("needs_replan_count")),
                number(row.get("rejected_count")));
    }

    private Map<String, Object> currentView(ChangePackageCurrent current) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", current.id());
        row.put("package_id", current.packageId());
        row.put("session_id", current.sessionId());
        row.put("incident_id", current.incidentId());
        row.put("project_id", current.projectId());
        row.put("preparation_agent_id", current.preparationAgentId());
        row.put("preparation_agent_version", current.preparationAgentVersion());
        row.put("package_type", current.packageType().name());
        row.put("status", current.status().name());
        row.put("version", current.version());
        row.put("package_hash", current.packageHash());
        row.put("approved_version", current.pointer().approvedVersion());
        row.put("approved_package_hash", current.pointer().approvedPackageHash());
        row.put("approved_snapshot_json",
                current.approvedSnapshot() == null ? null : current.approvedSnapshot().toMap());
        row.put("landing_run_id", current.landingRunId());
        row.put("create_by", current.createBy());
        row.put("approve_by", current.approveBy());
        row.put("create_time", current.createTime());
        row.put("update_time", current.updateTime());
        row.put("approved_at", current.approvedAt());
        row.put("approvedEpoch", current.approvedAt()==null ? null : current.approvedAt().getEpochSecond());
        current.state().values().forEach((field, value) -> row.put(columnName(field.snapshotKey()), value));
        return view(row);
    }

    /**
     * Admin history is intentionally tolerant. Runtime/detail/Landing continue to
     * use the strict ChangePackageCurrent mapper and therefore remain fail-closed.
     */
    private Map<String, Object> adminCurrentView(Map<String, Object> row) {
        Map<String, Object> result;
        try {
            result = view(row);
        } catch (RuntimeException exception) {
            result = minimalAdminCurrentView(row);
            markLegacyInvalid(result, reasonCode(exception));
            return result;
        }

        int approvedVersion = intValue(row.get("approved_version"));
        String approvedHash = text(row.get("approved_package_hash"));
        String approvedSnapshotJson = text(row.get("approved_snapshot_json"));
        boolean pointerApproved = approvedVersion > 0 && !approvedHash.isBlank();
        if (pointerApproved && approvedSnapshotJson.isBlank()) {
            markLegacyInvalid(result, "CHANGE_PACKAGE_VERSION_SNAPSHOT_REQUIRED");
        } else if (!pointerApproved && !approvedSnapshotJson.isBlank()) {
            markLegacyInvalid(result, "CHANGE_PACKAGE_UNAPPROVED_SNAPSHOT_PERSISTED");
        } else {
            result.put("legacyInvalid", false);
            result.put("invalidReason", "");
            result.put("runtimeExecutable", true);
        }
        return result;
    }

    private Map<String, Object> minimalAdminCurrentView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("id", "package_id", "session_id", "incident_id", "project_id",
                "preparation_agent_id", "preparation_agent_version", "package_type", "status", "version",
                "package_hash", "approved_version", "approved_package_hash", "landing_run_id", "create_by",
                "approve_by", "create_time", "update_time", "approved_at")) {
            if (row.containsKey(key)) result.put(key, row.get(key));
        }
        String[][] mappings = {
                {"package_id", "packageId"}, {"session_id", "sessionId"}, {"incident_id", "incidentId"},
                {"project_id", "projectId"}, {"preparation_agent_id", "preparationAgentId"},
                {"preparation_agent_version", "preparationAgentVersion"}, {"package_type", "packageType"},
                {"package_hash", "packageHash"}, {"approved_version", "approvedVersion"},
                {"approved_package_hash", "approvedPackageHash"}, {"landing_run_id", "landingRunId"},
                {"create_by", "createBy"}, {"approve_by", "approveBy"}, {"create_time", "createTime"},
                {"update_time", "updateTime"}, {"approved_at", "approvedAt"}
        };
        for (String[] mapping : mappings) camel(result, mapping[0], mapping[1]);
        return result;
    }

    private void markLegacyInvalid(Map<String, Object> result, String reason) {
        result.put("legacyInvalid", true);
        result.put("invalidReason", text(reason).isBlank() ? "CHANGE_PACKAGE_LEGACY_INVALID" : text(reason));
        result.put("runtimeExecutable", false);
    }

    private String reasonCode(RuntimeException exception) {
        String message = exception == null ? "" : text(exception.getMessage());
        return message.isBlank() ? "CHANGE_PACKAGE_LEGACY_INVALID" : message;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        String normalized = text(value);
        if (normalized.isBlank()) return 0;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void appendListEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND ").append(column).append("=?");
        args.add(value);
    }

    private Map<String, Object> versionView(ChangePackageVersion version) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", version.id());
        row.put("package_id", version.packageId());
        row.put("version", version.version());
        row.put("package_hash", version.packageHash());
        row.put("status", version.status());
        row.put("snapshot_json", version.snapshot().toMap());
        row.put("change_summary", version.changeSummary());
        row.put("created_by", version.createdBy());
        row.put("create_time", version.createdAt());
        return view(row);
    }

    private Map<String, Object> eventView(ChangePackageEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.id());
        row.put("event_id", event.eventId());
        row.put("package_id", event.packageId());
        row.put("event_type", event.eventType());
        row.put("actor", event.actor());
        row.put("summary", event.summary());
        row.put("payload", event.payload());
        row.put("payload_json", ChangePackageJsonMapCodec.encode(event.payload()));
        row.put("create_time", event.createdAt());
        return view(row);
    }

    private Map<String, Object> landingRunView(Map<String, Object> source) {
        Map<String, Object> data = view(source);
        camel(data, "operation_run_id", "operationRunId");
        camel(data, "landing_run_id", "landingRunId");
        camel(data, "approved_version", "approvedVersion");
        camel(data, "approved_package_hash", "approvedPackageHash");
        camel(data, "operation_id", "operationId");
        camel(data, "operation_hash", "operationHash");
        camel(data, "adapter_type", "adapterType");
        camel(data, "toolset_id", "toolsetId");
        camel(data, "tool_name", "toolName");
        camel(data, "resource_key", "resourceKey");
        camel(data, "effect_type", "effectType");
        camel(data, "fact_status", "factStatus");
        camel(data, "reason_code", "reasonCode");
        camel(data, "result_id", "resultId");
        camel(data, "output_hash", "outputHash");
        camel(data, "rollback_status", "rollbackStatus");
        camel(data, "rollback_at", "rollbackAt");
        camel(data, "started_at", "startedAt");
        camel(data, "finished_at", "finishedAt");
        putParsedAndRemove(data, "preconditionResult", "precondition_result_json", "preconditionResultJson");
        putParsedAndRemove(data, "executionResult", "execution_result_json", "executionResultJson");
        putParsedAndRemove(data, "postCheckResult", "post_check_result_json", "postCheckResultJson");
        putParsedAndRemove(data, "rollbackResult", "rollback_result_json", "rollbackResultJson");
        putParsedAndRemove(data, "result", "result_json", "resultJson");
        return data;
    }

    private void projectSnapshotFields(Map<String, Object> target, Map<String, Object> snapshot) {
        if (snapshot.isEmpty()) throw new IllegalStateException("ChangePackage 当前版本缺少可审核快照");
        for (String field : SNAPSHOT_PROJECTION_FIELDS) {
            Object value = firstExisting(snapshot, field, field + "Json");
            if (value != null) target.put(field, parseJsonValue(value));
        }
    }

    private Object firstExisting(Map<String, Object> source, String... keys) {
        for (String key : keys) if (source.containsKey(key)) return source.get(key);
        return null;
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        row.forEach((key, value) -> data.put(key,
                value instanceof Timestamp timestamp ? String.valueOf(timestamp) : value));
        String[][] mappings = {
                {"package_id", "packageId"}, {"session_id", "sessionId"},
                {"incident_id", "incidentId"}, {"project_id", "projectId"},
                {"preparation_agent_id", "preparationAgentId"},
                {"preparation_agent_version", "preparationAgentVersion"},
                {"package_type", "packageType"}, {"approved_version", "approvedVersion"},
                {"objective", "objective"}, {"summary", "summary"},
                {"package_hash", "packageHash"}, {"approved_package_hash", "approvedPackageHash"},
                {"approved_snapshot_json", "approvedSnapshotJson"}, {"risk_level", "riskLevel"},
                {"evidence_json", "evidenceJson"}, {"tool_bindings_json", "toolBindingsJson"},
                {"preflight_result_json", "preflightResultJson"}, {"dry_run_result_json", "dryRunResultJson"},
                {"validation_assessment", "validationAssessment"}, {"reason_code", "reasonCode"},
                {"approval_boundary_json", "approvalBoundaryJson"},
                {"preferred_plan_json", "preferredPlanJson"}, {"adjustment_policy_json", "adjustmentPolicyJson"},
                {"target_environment", "targetEnvironment"}, {"target_scope_json", "targetScopeJson"},
                {"allowed_tools_json", "allowedToolsJson"}, {"forbidden_tools_json", "forbiddenToolsJson"},
                {"branch_name", "branchName"}, {"base_branch", "baseBranch"},
                {"target_branch", "targetBranch"}, {"base_commit", "baseCommit"},
                {"repair_workspace_id", "repairWorkspaceId"}, {"repository_id", "repositoryId"},
                {"service_id", "serviceId"}, {"repair_commit", "repairCommit"},
                {"diff_summary", "diffSummary"}, {"diff_hash", "diffHash"},
                {"test_command", "testCommand"}, {"test_proof_hash", "testProofHash"},
                {"artifact_digest", "artifactDigest"}, {"changed_files_json", "changedFilesJson"},
                {"code_evidence_json", "codeEvidenceJson"}, {"bash_evidence_json", "bashEvidenceJson"},
                {"lsp_evidence_json", "lspEvidenceJson"}, {"mcp_steps_json", "mcpStepsJson"},
                {"rollback_steps_json", "rollbackStepsJson"},
                {"verification_criteria_json", "verificationCriteriaJson"},
                {"ci_result_json", "ciResultJson"}, {"landing_plan_json", "landingPlanJson"},
                {"allowed_landing_adjustments_json", "allowedLandingAdjustmentsJson"},
                {"landing_result_json", "landingResultJson"}, {"failure_summary_json", "failureSummaryJson"},
                {"cleanup_plan_json", "cleanupPlanJson"},
                {"create_by", "createBy"}, {"approve_by", "approveBy"},
                {"create_time", "createTime"}, {"update_time", "updateTime"},
                {"approved_at", "approvedAt"}, {"event_id", "eventId"},
                {"event_type", "eventType"}, {"payload_json", "payloadJson"},
                {"snapshot_json", "snapshotJson"}, {"change_summary", "changeSummary"},
                {"created_by", "createdBy"}, {"run_id", "runId"},
                {"landing_run_id", "landingRunId"}, {"idempotency_key", "idempotencyKey"},
                {"lease_token", "leaseToken"}, {"lease_expires_at", "leaseExpiresAt"},
                {"result_json", "resultJson"}, {"finished_at", "finishedAt"},
                {"resource_key", "resourceKey"}
        };
        for (String[] mapping : mappings) camel(data, mapping[0], mapping[1]);
        putParsedAndRemove(data, "evidence", "evidence_json", "evidenceJson");
        putParsedAndRemove(data, "approvedSnapshot", "approved_snapshot_json", "approvedSnapshotJson");
        putParsedAndRemove(data, "snapshot", "snapshot_json", "snapshotJson");
        putParsedAndRemove(data, "landingResult", "landing_result_json", "landingResultJson");
        putParsedAndRemove(data, "failureSummary", "failure_summary_json", "failureSummaryJson");
        putParsedAndRemove(data, "landingPlan", "landing_plan_json", "landingPlanJson");
        putParsedAndRemove(data, "mcpSteps", "mcp_steps_json", "mcpStepsJson");
        // Preserve the parsed aliases used by legacy/history views and also
        // expose the canonical camelCase fields consumed by the approval UI.
        // Without these mirrors a package with real steps and evidence appears
        // empty in the approval cockpit after JSON parsing.
        mirror(data, "evidence", "evidenceJson");
        mirror(data, "approvedSnapshot", "approvedSnapshotJson");
        mirror(data, "snapshot", "snapshotJson");
        mirror(data, "landingResult", "landingResultJson");
        mirror(data, "failureSummary", "failureSummaryJson");
        mirror(data, "landingPlan", "landingPlanJson");
        mirror(data, "mcpSteps", "mcpStepsJson");
        return data;
    }

    private void mirror(Map<String, Object> data, String parsedKey, String canonicalKey) {
        if (data.containsKey(parsedKey)) data.put(canonicalKey, data.get(parsedKey));
    }

    private void putParsedAndRemove(Map<String, Object> data, String target, String... sources) {
        Object value = null;
        for (String source : sources) if (value == null && data.containsKey(source)) value = data.get(source);
        if (value != null) data.put(target, parseJsonValue(value));
        for (String source : sources) data.remove(source);
    }

    private Object parseJsonValue(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) return value;
        String raw = text(value);
        if (raw.isBlank()) return Map.of();
        if (raw.startsWith("{")) return object(raw);
        if (raw.startsWith("[")) return JSON.parseArray(raw);
        return raw;
    }

    private Map<String, Object> object(String raw) {
        Map<String, Object> parsed = JSON.parseObject(raw,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        return parsed == null ? Map.of() : parsed;
    }

    private void camel(Map<String, Object> data, String source, String target) {
        if (data.containsKey(source)) data.put(target, data.get(source));
    }

    private String columnName(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private ChangePackageCurrent current(String packageId) {
        return currents().find(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("ChangePackage 不存在：" + packageId));
    }

    private ChangePackageVersion version(String packageId, int version) {
        return versions().find(packageId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ChangePackage 版本不存在：" + packageId + "@" + version));
    }

    private IChangePackageCurrentRepository currents() {
        if (!currentRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
        return currentRepository;
    }

    private IChangePackageVersionRepository versions() {
        if (!versionRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_VERSION_STORE_UNAVAILABLE");
        return versionRepository;
    }

    private IChangePackageEventRepository events() {
        if (!eventRepository.available()) throw new IllegalStateException("CHANGE_PACKAGE_EVENT_STORE_UNAVAILABLE");
        return eventRepository;
    }

    private JdbcTemplate template() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_QUERY_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        String normalized = text(value);
        return normalized.isBlank() ? 0L : Long.parseLong(normalized);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
