package cn.lgs.orbisops.application.project;

import java.util.List;

/** Product-facing readiness for diagnosis first value and controlled production remediation. */
public record ProjectProductReadinessProjection(
        Readiness diagnosis,
        Readiness remediation) {

    public ProjectProductReadinessProjection {
        if (diagnosis == null) throw new IllegalArgumentException("PROJECT_DIAGNOSIS_READINESS_REQUIRED");
        if (remediation == null) throw new IllegalArgumentException("PROJECT_REMEDIATION_READINESS_REQUIRED");
    }

    public record Readiness(
            boolean ready,
            List<Check> checks,
            List<String> missing,
            String nextAction) {
        public Readiness {
            checks = checks == null ? List.of() : List.copyOf(checks);
            missing = missing == null ? List.of() : List.copyOf(missing);
            nextAction = text(nextAction);
        }
    }

    public record Check(
            String key,
            String label,
            boolean ready,
            ReadinessLevel level,
            String detail) {
        public Check {
            key = required(key, "PROJECT_READINESS_CHECK_KEY_REQUIRED");
            label = required(label, "PROJECT_READINESS_CHECK_LABEL_REQUIRED");
            level = level == null ? ReadinessLevel.NOT_CONFIGURED : level;
            detail = text(detail);
        }
    }

    public enum ReadinessLevel {
        NOT_CONFIGURED,
        CONFIGURED,
        CONNECTIVITY_VERIFIED,
        QUERY_VERIFIED,
        AUTHORIZED,
        ENVIRONMENT_VALIDATED
    }

    private static String required(String value, String reason) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reason);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
