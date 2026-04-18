package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageOperationRisk;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageRiskInput;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical factory for immutable ChangePackage version snapshots. */
public final class ChangePackageSnapshotFactory {

    private static final ChangePackageRiskPolicy RISK_POLICY = new ChangePackageRiskPolicy();

    public ChangePackageSnapshot create(String packageId, int version, Map<String, Object> source, String actor) {
        return create(packageId, version, source, actor, Map.of());
    }

    public ChangePackageSnapshot create(String packageId,
                                        int version,
                                        Map<String, Object> source,
                                        String actor,
                                        Map<String, Object> trustedProofs) {
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_SNAPSHOT_VERSION_INVALID");
        Map<String, Object> input = source == null ? Map.of() : source;
        Map<String, Object> trusted = trustedProofs == null ? Map.of() : trustedProofs;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("packageId", required(packageId, "CHANGE_PACKAGE_SNAPSHOT_PACKAGE_ID_REQUIRED"));
        data.put("sessionId", text(input.get("sessionId"), ""));
        data.put("runId", text(input.get("runId"), ""));
        data.put("incidentId", text(input.get("incidentId"), ""));
        data.put("projectId", required(input.get("projectId"), "CHANGE_PACKAGE_PROJECT_ID_REQUIRED"));
        data.put("preparationAgentId", text(input.get("preparationAgentId"), text(input.get("agentId"), "")));
        data.put("preparationAgentVersion", intValue(input.get("preparationAgentVersion"),
                intValue(input.get("agentVersion"), 0)));
        data.put("preparationAgentSnapshot", objectValue(input.get("preparationAgentSnapshot")));
        data.put("packageType", packageType(input.get("packageType")).name());
        data.put("status", ChangePackageStatus.parseOrDraft(text(input.get("status"), "DRAFT")).name());
        data.put("version", version);
        data.put("objective", text(input.get("objective"), ""));
        data.put("summary", text(input.get("summary"), ""));
        data.put("evidenceJson", json(input.get("evidenceJson"), input.get("evidence")));
        data.put("toolBindingsJson", jsonArray(input.get("toolBindingsJson"), input.get("toolBindings")));
        data.put("preflightResultJson", json(snapshotProof(
                "preflight",
                trusted.get("preflightResult"),
                firstNonNull(input.get("preflightResultJson"), input.get("preflightResult")))));
        data.put("dryRunResultJson", json(snapshotProof(
                "dryRun",
                trusted.get("dryRunResult"),
                firstNonNull(input.get("dryRunResultJson"), input.get("dryRunResult")))));
        data.put("validationAssessment", text(input.get("validationAssessment"), ""));
        data.put("reasonCode", text(input.get("reasonCode"), ""));
        data.put("approvalBoundaryJson", json(input.get("approvalBoundaryJson"), input.get("approvalBoundary")));
        data.put("preferredPlanJson", json(input.get("preferredPlanJson"), input.get("preferredPlan")));
        data.put("adjustmentPolicyJson", json(input.get("adjustmentPolicyJson"), input.get("adjustmentPolicy")));
        data.put("riskLevel", effectiveRiskLevel(input));
        data.put("targetEnvironment", text(input.get("targetEnvironment"), ""));
        data.put("targetScopeJson", json(input.get("targetScopeJson"), input.get("targetScope")));
        data.put("allowedToolsJson", jsonArray(input.get("allowedToolsJson"), input.get("allowedTools")));
        data.put("forbiddenToolsJson", jsonArray(input.get("forbiddenToolsJson"), input.get("forbiddenTools")));
        data.put("branchName", text(input.get("branchName"), ""));
        data.put("baseBranch", text(input.get("baseBranch"), ""));
        data.put("targetBranch", text(input.get("targetBranch"), ""));
        data.put("baseCommit", text(input.get("baseCommit"), ""));
        data.put("repairCommit", text(input.get("repairCommit"), ""));
        data.put("diffSummary", text(input.get("diffSummary"), ""));
        data.put("changedFilesJson", jsonArray(input.get("changedFilesJson"), input.get("changedFiles")));
        data.put("mcpStepsJson", jsonArray(normalizeOperationEffects(
                firstNonNull(input.get("mcpStepsJson"), input.get("mcpSteps")))));
        data.put("rollbackStepsJson", jsonArray(input.get("rollbackStepsJson"), input.get("rollbackSteps")));
        data.put("verificationCriteriaJson", jsonArray(input.get("verificationCriteriaJson"), input.get("verificationCriteria")));
        data.put("ciResultJson", json(normalizeRequestProof("ci",
                firstNonNull(input.get("ciResultJson"), input.get("ciResult")))));
        data.put("landingPlanJson", json(normalizeLandingPlanEffects(
                firstNonNull(input.get("landingPlanJson"), input.get("landingPlan")))));
        data.put("allowedLandingAdjustmentsJson", jsonArray(normalizeAllowedLandingAdjustments(firstNonNull(
                input.get("allowedLandingAdjustmentsJson"), input.get("allowedLandingAdjustments")))));
        data.put("landingResultJson", json(input.get("landingResultJson"), input.get("landingResult")));
        data.put("failureSummaryJson", json(input.get("failureSummaryJson"), input.get("failureSummary")));
        data.put("cleanupPlanJson", json(input.get("cleanupPlanJson"), input.get("cleanupPlan")));
        data.put("repairWorkspaceId", text(input.get("repairWorkspaceId"), ""));
        data.put("repositoryId", text(input.get("repositoryId"), ""));
        data.put("serviceId", text(input.get("serviceId"), ""));
        data.put("diffHash", text(input.get("diffHash"), ""));
        data.put("testCommand", text(input.get("testCommand"), ""));
        data.put("testProofHash", text(input.get("testProofHash"), ""));
        data.put("artifactDigest", text(firstNonNull(input.get("artifactDigest"), input.get("artifactHash")), ""));
        data.put("codeEvidenceJson", json(normalizeEvidenceTrust("codeEvidence",
                firstNonNull(input.get("codeEvidenceJson"), input.get("codeEvidence")))));
        data.put("bashEvidenceJson", json(normalizeEvidenceTrust("bashEvidence",
                firstNonNull(input.get("bashEvidenceJson"), input.get("bashEvidence")))));
        data.put("lspEvidenceJson", json(normalizeEvidenceTrust("lspEvidence",
                firstNonNull(input.get("lspEvidenceJson"), input.get("lspEvidence")))));
        data.put("contextBundleId", required(input.get("contextBundleId"), "CHANGE_PACKAGE_CONTEXT_BUNDLE_REQUIRED"));
        data.put("contextBundleHash", required(input.get("contextBundleHash"), "CHANGE_PACKAGE_CONTEXT_BUNDLE_HASH_REQUIRED"));
        data.put("memoryContextRefsJson", jsonArray(input.get("memoryContextRefsJson"), input.get("memoryContextRefs")));
        data.put("memoryContextHash", text(input.get("memoryContextHash"), ""));
        data.put("compressedMemorySummary", text(input.get("compressedMemorySummary"), ""));
        data.put("memoryInjectionVersion", text(input.get("memoryInjectionVersion"), ""));
        data.put("usedSkillVersionRefsJson", jsonArray(input.get("usedSkillVersionRefsJson"), input.get("usedSkillVersionRefs")));
        data.put("usedSkillRefsHash", required(input.get("usedSkillRefsHash"), "CHANGE_PACKAGE_SKILL_REFS_HASH_REQUIRED"));
        data.put("toolsetRefsJson", jsonArray(input.get("toolsetRefsJson"), input.get("toolsetRefs")));
        data.put("policyRefsJson", jsonArray(input.get("policyRefsJson"), input.get("policyRefs")));
        data.put("policyHash", text(input.get("policyHash"), ""));
        data.put("toolsetBoundaryHash", required(input.get("toolsetBoundaryHash"), "CHANGE_PACKAGE_TOOLSET_BOUNDARY_HASH_REQUIRED"));
        data.put("runtimeBoundaryHash", required(input.get("runtimeBoundaryHash"), "CHANGE_PACKAGE_RUNTIME_BOUNDARY_HASH_REQUIRED"));
        data.put("contextApprovalBoundaryHash", text(input.get("contextApprovalBoundaryHash"), ""));
        data.put("approvalBoundaryHash", ChangePackageCanonicalHasher.canonicalHash(
                ChangePackageLegacyStructuredValue.decode(data.get("approvalBoundaryJson"))));
        data.put("trustedProofRefsJson", jsonArray(input.get("trustedProofRefsJson"), input.get("trustedProofRefs")));
        data.put("validationReportJson", json(input.get("validationReportJson"), input.get("validationReport")));
        data.put("usedSkillRefsJson", jsonArray(input.get("usedSkillRefsJson"), input.get("usedSkillRefs")));
        data.put("usedSkillVersionsJson", jsonArray(input.get("usedSkillVersionsJson"), input.get("usedSkillVersions")));
        data.put("usedSkillHashesJson", jsonArray(input.get("usedSkillHashesJson"), input.get("usedSkillHashes")));
        data.put("preparationMethodRefJson", json(input.get("preparationMethodRefJson"), input.get("preparationMethodRef")));
        data.put("preparationMethodHash", text(input.get("preparationMethodHash"), ""));
        data.put("preparationMethodSummary", text(input.get("preparationMethodSummary"), ""));
        data.put("createBy", text(input.get("createBy"), text(actor, "")));
        return ChangePackageSnapshot.seal(data);
    }

    private ChangePackageType packageType(Object value) {
        String normalized = text(value, ChangePackageType.MANUAL_REQUIRED.name()).toUpperCase(Locale.ROOT);
        try {
            return ChangePackageType.require(normalized);
        } catch (IllegalArgumentException ignored) {
            return ChangePackageType.MANUAL_REQUIRED;
        }
    }

    private String effectiveRiskLevel(Map<String, Object> source) {
        return RISK_POLICY.assess(riskInput(text(source.get("riskLevel"), "MEDIUM"), source)).effectiveRiskLevel();
    }

    private ChangePackageRiskInput riskInput(String requestedRisk, Map<String, Object> source) {
        Map<String, Object> landingPlan = objectValue(firstNonNull(source.get("landingPlan"), source.get("landingPlanJson")));
        Map<String, Object> preferredPlan = objectValue(landingPlan.get("preferredPlan"));
        List<Map<String, Object>> rawOperations = new ArrayList<>();
        for (Object candidate : List.of(
                firstNonNull(source.get("mcpSteps"), List.of()), firstNonNull(source.get("mcpStepsJson"), List.of()),
                firstNonNull(preferredPlan.get("steps"), List.of()), firstNonNull(preferredPlan.get("operations"), List.of()),
                firstNonNull(preferredPlan.get("mcpSteps"), List.of()), firstNonNull(landingPlan.get("steps"), List.of()),
                firstNonNull(landingPlan.get("operations"), List.of()), firstNonNull(landingPlan.get("mcpSteps"), List.of()))) {
            rawOperations.addAll(operations(candidate));
        }
        List<ChangePackageOperationRisk> risks = rawOperations.stream().map(operation -> new ChangePackageOperationRisk(
                text(firstNonNull(operation.get("operationId"), operation.get("operation_id")), ""),
                text(operation.get("riskLevel"), "HIGH"),
                text(firstNonNull(operation.get("effectType"), operation.get("effect_type")), "UNKNOWN"),
                text(firstNonNull(operation.get("effectScope"), operation.get("effect_scope")), "UNKNOWN"),
                text(operation.get("mutability"), "UNKNOWN"))).toList();
        return new ChangePackageRiskInput(requestedRisk, risks,
                stringList(firstNonNull(source.get("changedFiles"), source.get("changedFilesJson"))));
    }

    private Object normalizeOperationEffects(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Iterable<?> iterable)) return raw;
        List<Object> normalized = new ArrayList<>();
        iterable.forEach(item -> normalized.add(normalizeOperationEffect(item)));
        return normalized;
    }

    private Object normalizeOperationEffect(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Map<?, ?> map)) return raw;
        Map<String, Object> normalized = stringMap(map);
        if (normalized.containsKey("effectType")) {
            String effectType = text(normalized.get("effectType"), "").toUpperCase(Locale.ROOT);
            normalized.put("effectType", "MUTATE_TEMP_RESOURCE".equals(effectType) ? "MUTATE_EPHEMERAL" : effectType);
        }
        ChangePackageCanonicalHasher.applyOperationHashes(normalized);
        return normalized;
    }

    private Object normalizeLandingPlanEffects(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Map<?, ?> map)) return raw;
        Map<String, Object> normalized = stringMap(map);
        for (String key : List.of("mcpSteps", "operations", "steps")) {
            if (normalized.containsKey(key)) normalized.put(key, normalizeOperationEffects(normalized.get(key)));
        }
        if (normalized.containsKey("preferredPlan")) {
            normalized.put("preferredPlan", normalizePreferredPlanOperations(normalized.get("preferredPlan")));
        }
        return normalized;
    }

    private Object normalizePreferredPlanOperations(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Map<?, ?> map)) return raw;
        Map<String, Object> normalized = stringMap(map);
        for (String key : List.of("steps", "operations", "mcpSteps")) {
            if (normalized.containsKey(key)) normalized.put(key, normalizeOperationEffects(normalized.get(key)));
        }
        return normalized;
    }

    private Object snapshotProof(String proofType, Object trustedRaw, Object requestRaw) {
        if (trustedRaw == null) return normalizeRequestProof(proofType, requestRaw);
        Object parsed = ChangePackageLegacyStructuredValue.decode(trustedRaw);
        if (!(parsed instanceof Map<?, ?> map)) return normalizeRequestProof(proofType, requestRaw);
        Map<String, Object> data = stringMap(map);
        String source = text(firstNonNull(data.get("source"), data.get("proofSource")), "");
        if (Boolean.TRUE.equals(data.get("untrusted"))
                || "UNTRUSTED_USER_INPUT".equalsIgnoreCase(source)
                || "REQUEST_SUPPLIED_UNTRUSTED".equalsIgnoreCase(source)
                || "USER_INPUT".equalsIgnoreCase(source)
                || "REQUEST".equalsIgnoreCase(source)) {
            data.put("trustLevel", "PLAN_PORT_REJECTED_UNTRUSTED");
            data.put("trusted", false);
            data.put("untrusted", true);
            data.put("proofType", proofType);
            data.putIfAbsent("originalStatus", text(firstNonNull(data.get("status"), data.get("resultStatus")), ""));
            return data;
        }
        data.put("trustLevel", "PLAN_PORT_TRUSTED_PROVENANCE");
        data.put("trusted", true);
        data.put("untrusted", false);
        data.put("proofType", proofType);
        data.putIfAbsent("source", "PLAN_PORT_TRUSTED_PROVENANCE");
        data.putIfAbsent("originalStatus", text(firstNonNull(data.get("status"), data.get("resultStatus")), ""));
        return data;
    }

    private Object normalizeRequestProof(String proofType, Object raw) {
        if (raw == null) return Map.of();
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> data = stringMap(map);
            data.put("trustLevel", "REQUEST_SUPPLIED_UNTRUSTED");
            data.put("trusted", false);
            data.put("untrusted", true);
            data.put("source", "REQUEST_SUPPLIED_UNTRUSTED");
            data.put("proofType", proofType);
            data.put("originalStatus", text(firstNonNull(data.get("status"), data.get("resultStatus")), ""));
            return data;
        }
        if (parsed instanceof Iterable<?>) return parsed;
        return Map.of("value", text(parsed, ""), "trustLevel", "REQUEST_SUPPLIED_UNTRUSTED",
                "trusted", false, "untrusted", true, "source", "REQUEST_SUPPLIED_UNTRUSTED", "proofType", proofType);
    }

    private Object normalizeEvidenceTrust(String evidenceType, Object raw) {
        if (raw == null) return Map.of();
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> data = stringMap(map);
            data.putIfAbsent("trustLevel", "REQUEST_SUPPLIED_UNTRUSTED");
            data.putIfAbsent("source", "REQUEST_SUPPLIED_UNTRUSTED");
            data.put("evidenceType", evidenceType);
            String source = text(data.get("source"), "");
            if ("REQUEST".equalsIgnoreCase(source) || "USER_INPUT".equalsIgnoreCase(source)
                    || "REQUEST_SUPPLIED_UNTRUSTED".equalsIgnoreCase(source)) {
                data.put("trusted", false);
                data.put("untrusted", true);
            }
            return data;
        }
        return Map.of("items", parsed, "evidenceType", evidenceType, "trustLevel", "REQUEST_SUPPLIED_UNTRUSTED",
                "trusted", false, "untrusted", true);
    }

    private Object normalizeAllowedLandingAdjustments(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (parsed instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(item -> {
                if (item instanceof Map<?, ?> map && !text(map.get("type"), "").isBlank()) result.add(stringMap(map));
                else if (!text(item, "").isBlank()) result.add(Map.of("type", text(item, ""), "requiresRetest", true));
            });
            return result;
        }
        if (parsed instanceof Map<?, ?> map && map.containsKey("type")) return List.of(stringMap(map));
        return List.of();
    }

    private List<Map<String, Object>> operations(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) if (item instanceof Map<?, ?> map) result.add(stringMap(map));
        return result;
    }

    private List<String> stringList(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> result.add(text(item, "")));
        return result;
    }

    private Map<String, Object> objectValue(Object value) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(value);
        return parsed instanceof Map<?, ?> map ? stringMap(map) : Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String json(Object first, Object second) {
        Object value = first != null ? first : second;
        if (value == null) return "{}";
        if (value instanceof String string) return string.isBlank() ? "{}" : string.trim();
        return CanonicalJson.stringify(value);
    }

    private String json(Object value) {
        return json(value, null);
    }

    private String jsonArray(Object first, Object second) {
        Object value = first != null ? first : second;
        if (value == null) return "[]";
        if (value instanceof String string) return string.isBlank() ? "[]" : string.trim();
        return CanonicalJson.stringify(value);
    }

    private String jsonArray(Object value) {
        return jsonArray(value, null);
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }
}
