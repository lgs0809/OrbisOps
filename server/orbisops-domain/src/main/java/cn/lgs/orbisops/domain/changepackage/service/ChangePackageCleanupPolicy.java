package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ChangePackageCleanupPolicy {

    private static final Set<ChangePackageStatus> CLEANABLE = Set.of(
            ChangePackageStatus.LANDED,
            ChangePackageStatus.LANDING_FAILED,
            ChangePackageStatus.NEEDS_REPLAN,
            ChangePackageStatus.CLOSED);

    public void requireCleanable(ChangePackageCurrent current) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        if (!CLEANABLE.contains(current.status())) {
            throw new IllegalStateException("CHANGE_PACKAGE_NOT_CLEANABLE:" + current.status());
        }
    }

    public String boundWorkspaceId(ChangePackageCurrent current) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        String direct = text(current.state().nullable(ChangePackageCurrentField.REPAIR_WORKSPACE_ID));
        if (!direct.isBlank()) return direct;
        Map<String, Object> cleanupPlan = object(
                current.state().value(ChangePackageCurrentField.CLEANUP_PLAN_JSON));
        String fromCleanupPlan = firstNonBlank(
                cleanupPlan.get("repairWorkspaceId"), cleanupPlan.get("workspaceId"));
        if (!fromCleanupPlan.isBlank()) return fromCleanupPlan;
        Map<String, Object> evidence = object(
                current.state().value(ChangePackageCurrentField.EVIDENCE_JSON));
        return firstNonBlank(evidence.get("repairWorkspaceId"), evidence.get("workspaceId"));
    }

    private Map<String, Object> object(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Object decoded = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(decoded instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CLEANUP_BINDING_OBJECT_REQUIRED");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
