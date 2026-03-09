package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the authoritative current version and its legacy-compatible frozen snapshot. */
final class OpsPreApprovalSnapshotReader {

    private static final OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER =
            new OpsPreApprovalStructuredValueReader();

    private final ChangePackageQueryService queryService;

    OpsPreApprovalSnapshotReader(ChangePackageQueryService queryService) {
        if (queryService == null) throw new IllegalArgumentException("CHANGE_PACKAGE_QUERY_SERVICE_REQUIRED");
        this.queryService = queryService;
    }

    ValidationSnapshot load(String packageId, int submittedVersion) {
        Map<String, Object> current = queryService.detail(packageId);
        int currentVersion = intValue(current.get("version"), 0);
        if (submittedVersion > 0 && submittedVersion != currentVersion) {
            throw new IllegalStateException("只能验证当前 ChangePackage 版本：current=v"
                    + currentVersion + " submitted=v" + submittedVersion);
        }
        Map<String, Object> snapshot = queryService.versions(packageId).stream()
                .filter(item -> intValue(item.get("version"), 0) == currentVersion)
                .findFirst()
                .map(item -> STRUCTURED_VALUE_READER.object(
                        firstNonNull(item.get("snapshot"), item.get("snapshotJson"), item.get("snapshot_json"))))
                .orElseThrow(() -> new IllegalArgumentException(
                        "ChangePackage 版本不存在：" + packageId + "@" + currentVersion));
        String packageHash = text(firstNonNull(
                current.get("packageHash"), current.get("package_hash")), "");
        return new ValidationSnapshot(currentVersion, packageHash, current, snapshot);
    }

    Map<String, Object> detail(String packageId) {
        return queryService.detail(packageId);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value, ""));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record ValidationSnapshot(int version,
                              String packageHash,
                              Map<String, Object> current,
                              Map<String, Object> snapshot) {
        ValidationSnapshot {
            packageHash = packageHash == null ? "" : packageHash.trim();
            current = immutableCopy(current);
            snapshot = immutableCopy(snapshot);
        }

        private static Map<String, Object> immutableCopy(Map<String, Object> source) {
            return source == null || source.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
