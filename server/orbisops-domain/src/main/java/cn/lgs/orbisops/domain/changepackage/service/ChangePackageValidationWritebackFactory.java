package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageAggregate;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationWriteback;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the immutable post-validation version while preserving authoritative aggregate invariants. */
public final class ChangePackageValidationWritebackFactory {

    private static final ChangePackageSnapshotFactory SNAPSHOT_FACTORY = new ChangePackageSnapshotFactory();

    public ChangePackageValidationWriteback preparePassed(ChangePackageCurrent current,
                                                          ChangePackageVersion sourceVersion,
                                                          Map<String, Object> validationReport,
                                                          String actor) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        if (sourceVersion == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_REQUIRED");
        if (!current.packageId().equals(sourceVersion.packageId())) {
            throw new IllegalStateException("CHANGE_PACKAGE_VALIDATION_VERSION_PACKAGE_MISMATCH");
        }
        if (current.version() != sourceVersion.version()) {
            throw new IllegalStateException("CHANGE_PACKAGE_VALIDATION_SOURCE_VERSION_NOT_CURRENT");
        }
        if (!current.packageHash().equals(sourceVersion.packageHash())) {
            throw new IllegalStateException("CHANGE_PACKAGE_VALIDATION_SOURCE_HASH_MISMATCH");
        }

        ChangePackageAggregate aggregate = ChangePackageAggregate.rehydrate(current.pointer());
        aggregate.requireValidationOutcome(true);
        Map<String, Object> report = validationReport == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(validationReport);
        int nextVersion = current.version() + 1;
        String reasonCode = text(report.get("reasonCode"));
        if (reasonCode.isBlank()) reasonCode = "READY_FOR_REVIEW";
        String proofId = validationProofWritebackId(current.packageId(), nextVersion);

        Map<String, Object> merged = sourceVersion.snapshot().toMap();
        merged.put("packageId", current.packageId());
        merged.put("sessionId", current.sessionId());
        merged.put("incidentId", current.incidentId());
        merged.put("projectId", current.projectId());
        merged.put("preparationAgentId", current.preparationAgentId());
        merged.put("preparationAgentVersion", current.preparationAgentVersion());
        merged.put("packageType", current.packageType().name());
        current.state().values().forEach((field, value) -> merged.put(field.snapshotKey(), value));
        merged.put("version", nextVersion);
        merged.put("status", ChangePackageStatus.READY_FOR_REVIEW.name());
        merged.put("validationAssessment", "ACCEPTABLE");
        merged.put("reasonCode", reasonCode);
        merged.put("validationReport", report);

        List<Object> trustedProofRefs = new ArrayList<>(objectList(firstNonNull(
                report.get("trustedProofRefs"), report.get("proofRefs"), List.of())));
        trustedProofRefs.add(Map.of(
                "proofId", proofId,
                "proofType", "VALIDATION_PROOF_WRITEBACK",
                "sourcePackageVersion", current.version(),
                "sourcePackageHash", current.packageHash()));
        merged.put("trustedProofRefs", trustedProofRefs);
        merged.put("testProofHash", firstNonBlank(
                report.get("testProofHash"),
                current.state().nullable(ChangePackageCurrentField.TEST_PROOF_HASH)));
        merged.put("bashEvidence", firstNonNull(
                report.get("bashEvidence"), report.get("bashEvidenceJson"),
                current.state().value(ChangePackageCurrentField.BASH_EVIDENCE_JSON)));
        merged.put("ciResult", firstNonNull(
                report.get("ciResult"), report.get("ciResultJson"),
                current.state().value(ChangePackageCurrentField.CI_RESULT_JSON)));
        merged.put("failureSummary", Map.of());

        Map<String, Object> evidence = objectValue(current.state().value(ChangePackageCurrentField.EVIDENCE_JSON));
        evidence.put("validationReport", report);
        evidence.put("trustedProofRefs", trustedProofRefs);
        evidence.put("validationProofWriteback", Map.of(
                "version", nextVersion,
                "reasonCode", reasonCode,
                "actor", text(actor)));
        merged.put("evidence", evidence);

        ChangePackageSnapshot snapshot = SNAPSHOT_FACTORY.create(current.packageId(), nextVersion, merged, actor);
        ChangePackagePointer nextPointer = aggregate.validationPassed(nextVersion, snapshot.packageHash());
        return new ChangePackageValidationWriteback(snapshot, nextPointer, proofId,
                current.version(), current.packageHash(), reasonCode, report);
    }

    private String validationProofWritebackId(String packageId, int nextVersion) {
        String normalized = text(packageId).replaceAll("[^a-zA-Z0-9_\\-]", "-");
        if (normalized.isBlank()) normalized = "cp";
        return "vpf-" + normalized + "-v" + nextVersion;
    }

    private Map<String, Object> objectValue(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        Map<String, Object> result = new LinkedHashMap<>();
        if (parsed instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }

    private List<Map<String, Object>> objectList(Object raw) {
        Object parsed = ChangePackageLegacyStructuredValue.decode(raw);
        if (!(parsed instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> value = new LinkedHashMap<>();
                map.forEach((key, entry) -> value.put(String.valueOf(key), entry));
                result.add(value);
            }
        }
        return result;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
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
