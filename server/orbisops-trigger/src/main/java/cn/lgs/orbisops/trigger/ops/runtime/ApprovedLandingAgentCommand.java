package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/** Internal typed command emitted only after ChangePackage approval gates succeed. */
public record ApprovedLandingAgentCommand(
        String actor,
        String landingRunId,
        ChangePackageCurrent current,
        ChangePackageVersion approvedVersion,
        ChangePackageLandingPlan approvedPlan,
        ChangePackageLandingRequest landingRequest,
        ApprovedPackageSnapshot approvedPackage) {

    public ApprovedLandingAgentCommand {
        actor = required(actor, "LANDING_ACTOR_REQUIRED");
        landingRunId = required(landingRunId, "LANDING_RUN_ID_REQUIRED");
        if (current == null) throw new IllegalArgumentException("LANDING_CURRENT_REQUIRED");
        if (approvedVersion == null) throw new IllegalArgumentException("LANDING_APPROVED_VERSION_REQUIRED");
        if (approvedPlan == null) throw new IllegalArgumentException("LANDING_APPROVED_PLAN_REQUIRED");
        if (landingRequest == null) throw new IllegalArgumentException("LANDING_REQUEST_REQUIRED");
        if (approvedPackage == null) throw new IllegalArgumentException("APPROVED_PACKAGE_REQUIRED");
        if (!current.packageId().equals(approvedPackage.packageId())) {
            throw new IllegalArgumentException("LANDING_PACKAGE_ID_MISMATCH");
        }
        if (!current.projectId().equals(approvedPackage.projectId())) {
            throw new IllegalArgumentException("LANDING_PROJECT_ID_MISMATCH");
        }
        if (approvedVersion.version() != approvedPackage.packageVersion()) {
            throw new IllegalArgumentException("LANDING_PACKAGE_VERSION_MISMATCH");
        }
        if (!approvedVersion.packageHash().equals(approvedPackage.packageHash())) {
            throw new IllegalArgumentException("LANDING_PACKAGE_HASH_MISMATCH");
        }
        if (!approvedPlan.packageId().equals(approvedPackage.packageId())
                || !approvedPlan.projectId().equals(approvedPackage.projectId())
                || approvedPlan.approvedVersion() != approvedPackage.packageVersion()
                || !approvedPlan.approvedPackageHash().equals(approvedPackage.packageHash())) {
            throw new SecurityException("LANDING_APPROVED_PLAN_POINTER_MISMATCH");
        }
    }

    public String landingRuntimeId() {
        return OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_ID;
    }

    public int landingRuntimeVersion() {
        return OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_VERSION;
    }

    public String workSessionId() {
        // Landing is its own durable Work Session. Reusing the landing run id keeps the
        // persisted session scope bounded by the platform's VARCHAR(80) identifier contract.
        // The originating PREPARE session is preserved separately as provenance metadata.
        return landingRunId;
    }

    public String instruction() {
        return OpsPlatformLandingRuntimeDefinitionFactory.instruction()
                + "\n\nApproved ChangePackage task book (server-frozen JSON):\n"
                + JSON.toJSONString(taskBook())
                + "\n\nExecute this approved task book rather than rediscovering its target from user conversation history. "
                + "The operation arguments are approved baseline business arguments; you may adapt execution details only while the approved plan remains valid. "
                + "Runtime-owned project identity, actor, idempotency execution key and authority deadline are supplied by the platform execution boundary. "
                + "Return NEEDS_REPLAN only when continuing would materially change the approved plan itself.";
    }

    private Map<String, Object> taskBook() {
        Map<String, Object> snapshot = approvedPlan.snapshot();
        Map<String, Object> book = new LinkedHashMap<>();
        book.put("schemaVersion", "approved-landing-task-book-v1");
        book.put("packageId", approvedPackage.packageId());
        book.put("packageVersion", approvedPackage.packageVersion());
        book.put("packageHash", approvedPackage.packageHash());
        book.put("projectId", approvedPackage.projectId());
        book.put("targetEnvironment", approvedPackage.targetEnvironment());
        if (!approvedPackage.artifactDigest().isBlank()) {
            book.put("artifactDigest", approvedPackage.artifactDigest());
        }
        putIfPresent(book, "objective", first(snapshot, "objective", "userObjective"));
        putIfPresent(book, "summary", first(snapshot, "summary"));
        putIfPresent(book, "preferredPlan", first(snapshot, "preferredPlan", "preferredPlanJson"));
        putIfPresent(book, "approvalBoundary", first(snapshot, "approvalBoundary", "approvalBoundaryJson"));
        putIfPresent(book, "verificationCriteria", first(snapshot, "verificationCriteria", "verificationCriteriaJson"));
        putIfPresent(book, "rollback", first(snapshot, "rollbackSteps", "rollbackStepsJson", "rollbackPlan"));
        book.put("operations", approvedPlan.operations().stream().map(this::operationTask).toList());
        book.put("runtimeOwnedArguments", Map.of(
                "projectId", "platform-injected",
                "actor", "platform-injected",
                "executionKey", "platform-injected idempotency key",
                "deadline", "platform-injected authority deadline"));
        return Map.copyOf(book);
    }

    private Map<String, Object> operationTask(cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation operation) {
        Map<String, Object> raw = operation.raw();
        Map<String, Object> task = new LinkedHashMap<>();
        putIfPresent(task, "operationId", operation.operationId());
        putIfPresent(task, "operationHash", operation.operationHash());
        putIfPresent(task, "adapterType", operation.adapterType());
        putIfPresent(task, "toolsetId", operation.toolsetId());
        putIfPresent(task, "toolName", operation.toolName());
        putIfPresent(task, "resourceKey", operation.resourceKey());
        putIfPresent(task, "effectType", operation.effectType());
        for (String key : new String[]{
                "mcpId", "remoteToolName", "targetEnvironment", "resourceScope", "riskLevel", "mutability",
                "arguments", "preconditions", "verification", "postCheck", "rollback", "rollbackPlan", "manualFallback",
                "policyId", "schemaHash", "requiresDryRun", "requiresRollbackPlan"}) {
            putIfPresent(task, key, raw.get(key));
        }
        return Map.copyOf(task);
    }

    private Object first(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) return null;
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return value;
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        target.put(key, value);
    }

    private static String required(Object value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
