package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalActorContext;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalAssessment;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalReview;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageOperationRisk;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageRiskInput;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds the domain approval review from the authoritative current row and immutable version. */
public final class ChangePackageApprovalAssessmentFactory {

    private static final ChangePackageApprovalPolicy APPROVAL_POLICY = new ChangePackageApprovalPolicy();
    private static final ChangePackageRiskPolicy RISK_POLICY = new ChangePackageRiskPolicy();
    private static final Set<ChangePackageType> VALIDATION_PROOF_EXEMPT_TYPES = Set.of(
            ChangePackageType.NO_ACTION_REQUIRED,
            ChangePackageType.MANUAL_REQUIRED,
            ChangePackageType.NEEDS_HUMAN_DESIGN);

    public ChangePackageApprovalAssessment assess(ChangePackageCurrent current,
                                                   ChangePackageVersion version,
                                                   String approver,
                                                   ChangePackageApprovalActorContext approvalContext) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        if (version == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_REQUIRED");
        String normalizedApprover = required(approver, "CHANGE_PACKAGE_APPROVER_REQUIRED");
        if (!current.packageId().equals(version.packageId())) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_VERSION_PACKAGE_MISMATCH");
        }

        ChangePackageSnapshot snapshot = version.snapshot();
        Map<String, Object> snapshotValues = snapshot.toMap();
        ChangePackageApprovalActorContext actorContext = approvalContext == null
                ? ChangePackageApprovalActorContext.anonymous()
                : approvalContext;
        String actorScope = actorContext.actorScope();
        boolean adminConfirmation = actorContext.adminConfirmation();
        String riskLevel = RISK_POLICY.assess(riskInput(
                current.state().value(ChangePackageCurrentField.RISK_LEVEL), snapshotValues)).effectiveRiskLevel();
        String creator = firstNonBlank(current.createBy(), snapshotValues.get("createBy"));
        String validationAssessment = current.state().value(ChangePackageCurrentField.VALIDATION_ASSESSMENT);
        ChangePackageType packageType = packageType(snapshotValues, current.packageType());

        ChangePackageApprovalPolicy.ApprovalRequirement requirement = APPROVAL_POLICY.evaluate(
                new ChangePackageApprovalReview(
                        riskLevel,
                        creator,
                        normalizedApprover,
                        actorScope,
                        adminConfirmation,
                        !VALIDATION_PROOF_EXEMPT_TYPES.contains(packageType),
                        validationAssessment,
                        rollbackMaterialPresent(snapshotValues)));
        return new ChangePackageApprovalAssessment(snapshot, riskLevel, requirement, actorScope, adminConfirmation);
    }

    private ChangePackageType packageType(Map<String, Object> snapshot, ChangePackageType fallback) {
        String value = text(snapshot.get("packageType"));
        return value.isBlank() ? fallback : ChangePackageType.require(value);
    }

    private ChangePackageRiskInput riskInput(String requestedRisk, Map<String, Object> source) {
        Map<String, Object> landingPlan = objectValue(firstNonNull(source.get("landingPlan"), source.get("landingPlanJson")));
        Map<String, Object> preferredPlan = objectValue(landingPlan.get("preferredPlan"));
        List<Map<String, Object>> rawOperations = new ArrayList<>();
        for (Object candidate : List.of(
                firstNonNull(source.get("mcpSteps"), List.of()),
                firstNonNull(source.get("mcpStepsJson"), List.of()),
                firstNonNull(preferredPlan.get("steps"), List.of()),
                firstNonNull(preferredPlan.get("operations"), List.of()),
                firstNonNull(preferredPlan.get("mcpSteps"), List.of()),
                firstNonNull(landingPlan.get("steps"), List.of()),
                firstNonNull(landingPlan.get("operations"), List.of()),
                firstNonNull(landingPlan.get("mcpSteps"), List.of()))) {
            rawOperations.addAll(operations(candidate));
        }
        List<ChangePackageOperationRisk> operationRisks = rawOperations.stream()
                .map(operation -> new ChangePackageOperationRisk(
                        text(firstNonNull(operation.get("operationId"), operation.get("operation_id"))),
                        fallbackText(operation.get("riskLevel"), "HIGH"),
                        fallbackText(firstNonNull(operation.get("effectType"), operation.get("effect_type")), "UNKNOWN"),
                        fallbackText(firstNonNull(operation.get("effectScope"), operation.get("effect_scope")), "UNKNOWN"),
                        fallbackText(operation.get("mutability"), "UNKNOWN")))
                .toList();
        return new ChangePackageRiskInput(requestedRisk, operationRisks,
                stringList(firstNonNull(source.get("changedFiles"), source.get("changedFilesJson"))));
    }

    private boolean rollbackMaterialPresent(Map<String, Object> snapshot) {
        if (!operations(firstNonNull(snapshot.get("rollbackSteps"), snapshot.get("rollbackStepsJson"))).isEmpty()) {
            return true;
        }
        if (!objectValue(snapshot.get("rollbackPlan")).isEmpty()) {
            return true;
        }
        for (Map<String, Object> operation : operations(firstNonNull(
                snapshot.get("mcpSteps"), snapshot.get("mcpStepsJson"),
                objectValue(snapshot.get("landingPlan")).get("mcpSteps")))) {
            if (!objectValue(firstNonNull(operation.get("rollbackPlan"), operation.get("rollbackSteps"))).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> objectValue(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return Map.of();
    }

    private List<Map<String, Object>> operations(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> data = new LinkedHashMap<>();
                map.forEach((key, value) -> data.put(String.valueOf(key), value));
                result.add(data);
            }
        }
        return result;
    }

    private List<String> stringList(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> result.add(text(item)));
        return result;
    }

    private String fallbackText(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
