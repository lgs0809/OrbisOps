package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Authoritative operation fact used by Landing recovery and reconciliation. */
public record LandingOperationFact(
        String operationId,
        FactStatus factStatus,
        ExecutionStatus executionStatus,
        String reasonCode,
        String resultId,
        String outputHash,
        Map<String, Object> payload) {

    public LandingOperationFact {
        operationId = text(operationId);
        if (factStatus == null) factStatus = FactStatus.OTHER;
        if (executionStatus == null) executionStatus = ExecutionStatus.OTHER;
        reasonCode = text(reasonCode);
        resultId = text(resultId);
        outputHash = text(outputHash);
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public boolean unknown() {
        return factStatus == FactStatus.UNKNOWN;
    }

    public boolean completed() {
        return factStatus == FactStatus.COMPLETED;
    }

    public boolean succeeded() {
        return completed() && executionStatus == ExecutionStatus.SUCCEEDED;
    }

    public enum FactStatus {
        NONE,
        UNKNOWN,
        COMPLETED,
        OTHER;

        public static FactStatus from(String value) {
            return parse(value, FactStatus.class, OTHER);
        }
    }

    public enum ExecutionStatus {
        PENDING,
        PRECONDITION_CHECKING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        BLOCKED,
        UNKNOWN,
        OTHER;

        public static ExecutionStatus from(String value) {
            return parse(value, ExecutionStatus.class, OTHER);
        }
    }

    private static <E extends Enum<E>> E parse(String value, Class<E> type, E fallback) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
