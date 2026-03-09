package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reconstructs the only executable landing plan from the frozen approved version. */
public final class ChangePackageLandingPlanFactory {

    private static final ChangePackageLandingOperationSafetyPolicy SAFETY_POLICY =
            new ChangePackageLandingOperationSafetyPolicy();

    public ChangePackageLandingPlan create(ChangePackageCurrent current, ChangePackageVersion approvedVersion) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        if (approvedVersion == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_REQUIRED");
        if (!current.pointer().approved()) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_REQUIRED");
        }
        if (!current.packageId().equals(approvedVersion.packageId())) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_VERSION_PACKAGE_MISMATCH");
        }
        if (current.pointer().approvedVersion() != approvedVersion.version()) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_APPROVED_VERSION_MISMATCH");
        }
        if (!current.pointer().approvedPackageHash().equals(approvedVersion.packageHash())) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_APPROVED_HASH_MISMATCH");
        }
        Map<String, Object> snapshot = approvedVersion.snapshot().toMap();
        String embeddedPackageId = firstNonBlank(snapshot.get("packageId"), current.packageId());
        String embeddedProjectId = firstNonBlank(snapshot.get("projectId"), current.projectId());
        int embeddedVersion = intValue(snapshot.get("version"));
        String embeddedHash = firstNonBlank(snapshot.get("packageHash"), approvedVersion.packageHash());
        if (!current.packageId().equals(embeddedPackageId)
                || !current.projectId().equals(embeddedProjectId)
                || embeddedVersion != approvedVersion.version()
                || !approvedVersion.packageHash().equals(embeddedHash)) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_SNAPSHOT_IDENTITY_MISMATCH");
        }
        List<ChangePackageLandingOperation> operations = operations(snapshot).stream()
                .filter(SAFETY_POLICY::targetWrite)
                .map(this::operation)
                .toList();
        return new ChangePackageLandingPlan(current.packageId(), current.projectId(),
                approvedVersion.version(), approvedVersion.packageHash(), snapshot, operations);
    }

    private ChangePackageLandingOperation operation(Map<String, Object> raw) {
        String operationId = firstNonBlank(raw.get("operationId"), raw.get("operation_id"));
        return new ChangePackageLandingOperation(
                operationId,
                firstNonBlank(raw.get("operationHash"), raw.get("operation_hash")),
                firstNonBlank(raw.get("adapterType"), raw.get("adapter_type"),
                        text(raw.get("mcpId")).isBlank() ? "" : "MCP"),
                firstNonBlank(raw.get("toolsetId"), raw.get("toolset_id"), raw.get("mcpId")),
                firstNonBlank(raw.get("toolName"), raw.get("tool_name"), raw.get("remoteToolName")),
                firstNonBlank(raw.get("resourceKey"), raw.get("resource_key"), raw.get("resourceScope")),
                firstNonBlank(raw.get("effectType"), raw.get("effect_type")),
                raw);
    }

    private List<Map<String, Object>> operations(Map<String, Object> snapshot) {
        Map<String, Object> landingPlan = map(firstNonNull(snapshot.get("landingPlan"), snapshot.get("landingPlanJson")));
        Map<String, Object> preferred = map(landingPlan.get("preferredPlan"));
        List<Map<String, Object>> result = list(firstNonNull(
                preferred.get("steps"), preferred.get("operations"), preferred.get("mcpSteps")));
        if (!result.isEmpty()) return result;
        result = list(firstNonNull(landingPlan.get("steps"), landingPlan.get("operations"), landingPlan.get("mcpSteps")));
        if (!result.isEmpty()) return result;
        return list(firstNonNull(snapshot.get("mcpSteps"), snapshot.get("mcpStepsJson")));
    }

    private Map<String, Object> map(Object raw) {
        Object value = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> list(Object raw) {
        Object value = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            Map<String, Object> mapped = map(item);
            if (!mapped.isEmpty()) result.add(mapped);
        }
        return result;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
