package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Expired UNKNOWN Landing operation eligible for authoritative reconciliation. */
public record LandingOperationRecoveryCandidate(
        String operationRunId,
        String landingRunId,
        String packageId,
        String projectId,
        int approvedVersion,
        String approvedPackageHash,
        String operationId,
        String operationHash,
        String executionKey,
        String adapterType,
        String toolsetId,
        String toolName,
        String resourceKey,
        String effectType,
        long stateVersion,
        long fencingToken,
        Map<String, Object> operation) {

    public LandingOperationRecoveryCandidate {
        operation = operation == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(operation));
    }
}
