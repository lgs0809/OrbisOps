package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads typed ChangePackageVersion snapshots and legacy nested plan representations. */
final class OpsChangePackageApprovalSnapshotReader {

    private static final OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER =
            new OpsPreApprovalStructuredValueReader();

    ApprovalSnapshot read(ChangePackageVersion version) {
        if (version == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_REQUIRED");
        Map<String, Object> snapshot = version.snapshot().toMap();
        return new ApprovalSnapshot(snapshot, operations(snapshot));
    }

    List<String> stringList(Object value) {
        return STRUCTURED_VALUE_READER.stringList(value);
    }

    Map<String, Object> object(Object value) {
        return STRUCTURED_VALUE_READER.object(value);
    }

    private List<Map<String, Object>> operations(Map<String, Object> snapshot) {
        Map<String, Object> landingPlan = STRUCTURED_VALUE_READER.object(firstNonNull(
                snapshot.get("landingPlan"), snapshot.get("landingPlanJson")));
        Map<String, Object> preferredPlan = STRUCTURED_VALUE_READER.object(landingPlan.get("preferredPlan"));
        List<Map<String, Object>> preferredSteps = STRUCTURED_VALUE_READER.operations(firstNonNull(
                preferredPlan.get("steps"),
                preferredPlan.get("operations"),
                preferredPlan.get("mcpSteps")));
        if (!preferredSteps.isEmpty()) return preferredSteps;
        List<Map<String, Object>> landingSteps = STRUCTURED_VALUE_READER.operations(firstNonNull(
                landingPlan.get("steps"),
                landingPlan.get("operations"),
                landingPlan.get("mcpSteps")));
        if (!landingSteps.isEmpty()) return landingSteps;
        return STRUCTURED_VALUE_READER.operations(firstNonNull(
                snapshot.get("mcpSteps"), snapshot.get("mcpStepsJson")));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    record ApprovalSnapshot(Map<String, Object> values,
                            List<Map<String, Object>> operations) {
        ApprovalSnapshot {
            values = values == null || values.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(values));
            operations = operations == null
                    ? List.of()
                    : operations.stream()
                    .map(item -> item == null
                            ? Map.<String, Object>of()
                            : Collections.unmodifiableMap(new LinkedHashMap<>(item)))
                    .toList();
        }
    }
}
