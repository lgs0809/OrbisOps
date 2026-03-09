package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChangePackageLandingOutcome(String packageId,
                                          String landingRunId,
                                          String idempotencyKey,
                                          ChangePackageStatus status,
                                          boolean idempotentReplay,
                                          Map<String, Object> result) {

    public ChangePackageLandingOutcome {
        packageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        landingRunId = required(landingRunId, "CHANGE_PACKAGE_LANDING_RUN_ID_REQUIRED");
        idempotencyKey = required(idempotencyKey, "CHANGE_PACKAGE_LANDING_IDEMPOTENCY_KEY_REQUIRED");
        if (status == null) throw new IllegalArgumentException("CHANGE_PACKAGE_STATUS_REQUIRED");
        result = result == null || result.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
