package cn.lgs.orbisops.domain.toolset.model.business;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/** Fixed typed request for the first approved business write MCP Tool. */
public record UpdateAlertThresholdCommand(
        String projectId,
        String metric,
        BigDecimal expectedValue,
        long expectedVersion,
        BigDecimal newValue,
        String approvalId,
        String executionKey,
        Instant deadline,
        String actor
) {

    private static final Pattern METRIC =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final BigDecimal MAX_ABSOLUTE_VALUE =
            new BigDecimal("1000000000000");

    public UpdateAlertThresholdCommand {
        projectId = required(projectId, "UPDATE_ALERT_THRESHOLD_PROJECT_REQUIRED");
        metric = required(metric, "UPDATE_ALERT_THRESHOLD_METRIC_REQUIRED");
        if (!METRIC.matcher(metric).matches()) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_METRIC_INVALID");
        }
        expectedValue = number(expectedValue, "UPDATE_ALERT_THRESHOLD_EXPECTED_VALUE_REQUIRED");
        newValue = number(newValue, "UPDATE_ALERT_THRESHOLD_NEW_VALUE_REQUIRED");
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_EXPECTED_VERSION_INVALID");
        }
        if (expectedValue.abs().compareTo(MAX_ABSOLUTE_VALUE) > 0
                || newValue.abs().compareTo(MAX_ABSOLUTE_VALUE) > 0) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_VALUE_OUT_OF_RANGE");
        }
        if (expectedValue.compareTo(newValue) == 0) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_NO_CHANGE");
        }
        approvalId = required(approvalId, "UPDATE_ALERT_THRESHOLD_APPROVAL_REQUIRED");
        executionKey = required(executionKey, "UPDATE_ALERT_THRESHOLD_EXECUTION_KEY_REQUIRED");
        if (deadline == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_DEADLINE_REQUIRED");
        }
        actor = required(actor, "UPDATE_ALERT_THRESHOLD_ACTOR_REQUIRED");
    }

    public void validateDeadline(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_CLOCK_REQUIRED");
        if (!deadline.isAfter(clock.instant())) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_DEADLINE_EXPIRED");
        }
    }

    public static UpdateAlertThresholdCommand from(
            Map<String, Object> arguments,
            String projectId,
            String executionKey,
            Instant deadline,
            String actor) {
        Map<String, Object> source = arguments == null ? Map.of() : arguments;
        return new UpdateAlertThresholdCommand(
                consistentText(
                        projectId,
                        source.get("projectId"),
                        "UPDATE_ALERT_THRESHOLD_PROJECT_CONTEXT_MISMATCH"),
                text(source.get("metric")),
                decimal(source.get("expectedValue"), "UPDATE_ALERT_THRESHOLD_EXPECTED_VALUE_REQUIRED"),
                longValue(source.get("expectedVersion"), "UPDATE_ALERT_THRESHOLD_EXPECTED_VERSION_REQUIRED"),
                decimal(source.get("newValue"), "UPDATE_ALERT_THRESHOLD_NEW_VALUE_REQUIRED"),
                text(source.get("approvalId")),
                consistentText(
                        executionKey,
                        source.get("executionKey"),
                        "UPDATE_ALERT_THRESHOLD_EXECUTION_KEY_CONTEXT_MISMATCH"),
                consistentInstant(
                        deadline,
                        source.get("deadline"),
                        "UPDATE_ALERT_THRESHOLD_DEADLINE_CONTEXT_MISMATCH"),
                consistentText(
                        actor,
                        source.get("actor"),
                        "UPDATE_ALERT_THRESHOLD_ACTOR_CONTEXT_MISMATCH"));
    }

    private static BigDecimal number(BigDecimal value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value.stripTrailingZeros();
    }

    private static BigDecimal decimal(Object value, String reasonCode) {
        try {
            String normalized = text(value);
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return new BigDecimal(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(reasonCode, error);
        }
    }

    private static long longValue(Object value, String reasonCode) {
        try {
            String normalized = text(value);
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return Long.parseLong(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(reasonCode, error);
        }
    }

    private static Instant instant(Object value) {
        try {
            String normalized = text(value);
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_DEADLINE_REQUIRED");
            }
            return Instant.parse(normalized);
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal
                    && "UPDATE_ALERT_THRESHOLD_DEADLINE_REQUIRED".equals(illegal.getMessage())) {
                throw illegal;
            }
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_DEADLINE_INVALID", error);
        }
    }

    private static String consistentText(
            Object authoritative,
            Object supplied,
            String mismatchReasonCode) {
        String expected = text(authoritative);
        String actual = text(supplied);
        if (!expected.isBlank() && !actual.isBlank() && !expected.equals(actual)) {
            throw new IllegalArgumentException(mismatchReasonCode);
        }
        return !expected.isBlank() ? expected : actual;
    }

    private static Instant consistentInstant(
            Instant authoritative,
            Object supplied,
            String mismatchReasonCode) {
        String raw = text(supplied);
        Instant actual = raw.isBlank() ? null : instant(raw);
        if (authoritative != null && actual != null && !authoritative.equals(actual)) {
            throw new IllegalArgumentException(mismatchReasonCode);
        }
        return authoritative != null ? authoritative : actual;
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
