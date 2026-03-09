package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Named envelope for stable validation outcome facts plus open proof payload. */
public record ChangePackageValidationReport(
        String reasonCode,
        String executionToken,
        Map<String, Object> proofFacts) {

    public ChangePackageValidationReport {
        reasonCode = text(reasonCode);
        executionToken = text(executionToken);
        proofFacts = immutable(proofFacts);
    }

    public static ChangePackageValidationReport from(Map<String, ?> source) {
        Map<String, Object> facts = mutable(source);
        String reasonCode = text(facts.remove("reasonCode"));
        String executionToken = text(facts.remove("_validationExecutionToken"));
        return new ChangePackageValidationReport(reasonCode, executionToken, facts);
    }

    public String effectiveReasonCode(boolean passed) {
        if (!reasonCode.isBlank()) return reasonCode;
        return passed ? "READY_FOR_REVIEW" : "VALIDATION_FAILED";
    }

    public Map<String, Object> eventPayload(boolean passed) {
        Map<String, Object> payload = new LinkedHashMap<>(proofFacts);
        payload.put("reasonCode", effectiveReasonCode(passed));
        return Collections.unmodifiableMap(payload);
    }

    public Map<String, Object> proofPayload() {
        return proofFacts;
    }

    private static Map<String, Object> mutable(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
