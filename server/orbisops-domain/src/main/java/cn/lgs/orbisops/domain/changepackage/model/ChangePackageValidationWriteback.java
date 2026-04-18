package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Domain result for a successful validation proof writeback. */
public record ChangePackageValidationWriteback(ChangePackageSnapshot snapshot,
                                               ChangePackagePointer nextPointer,
                                               String proofId,
                                               int sourceVersion,
                                               String sourcePackageHash,
                                               String reasonCode,
                                               Map<String, Object> validationReport) {

    public ChangePackageValidationWriteback {
        if (snapshot == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_SNAPSHOT_REQUIRED");
        if (nextPointer == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_POINTER_REQUIRED");
        proofId = required(proofId, "CHANGE_PACKAGE_VALIDATION_PROOF_ID_REQUIRED");
        if (sourceVersion <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_SOURCE_VERSION_INVALID");
        sourcePackageHash = required(sourcePackageHash, "CHANGE_PACKAGE_VALIDATION_SOURCE_HASH_REQUIRED");
        reasonCode = required(reasonCode, "CHANGE_PACKAGE_VALIDATION_REASON_REQUIRED");
        validationReport = validationReport == null || validationReport.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(validationReport));
    }

    public int targetVersion() {
        return nextPointer.version();
    }

    public String targetPackageHash() {
        return nextPointer.packageHash();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
