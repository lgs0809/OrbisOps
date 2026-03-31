package cn.lgs.orbisops.domain.toolset.model.business;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/** Authoritative downstream receipt for update or state-restore threshold operations. */
public record UpdateAlertThresholdReceipt(
        String status,
        String receiptId,
        String operation,
        String executionKey,
        String projectId,
        String metric,
        String approvalId,
        String actor,
        long expectedVersion,
        int hashVersion,
        String operationInputHash,
        BigDecimal previousValue,
        BigDecimal currentValue,
        long currentVersion,
        String resultHash,
        Instant completedAt
) {

    private static final Pattern RESULT_HASH =
            Pattern.compile("sha256:[a-fA-F0-9]{64}");
    private static final Pattern INPUT_HASH =
            Pattern.compile("[a-fA-F0-9]{64}");

    public UpdateAlertThresholdReceipt {
        status = required(status, "UPDATE_ALERT_THRESHOLD_RECEIPT_STATUS_REQUIRED").toUpperCase();
        if (!"SUCCEEDED".equals(status)) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_NOT_SUCCEEDED");
        }
        receiptId = required(receiptId, "UPDATE_ALERT_THRESHOLD_RECEIPT_ID_REQUIRED");
        operation = required(operation, "UPDATE_ALERT_THRESHOLD_RECEIPT_OPERATION_REQUIRED");
        executionKey = required(executionKey, "UPDATE_ALERT_THRESHOLD_RECEIPT_EXECUTION_KEY_REQUIRED");
        projectId = required(projectId, "UPDATE_ALERT_THRESHOLD_RECEIPT_PROJECT_REQUIRED");
        metric = required(metric, "UPDATE_ALERT_THRESHOLD_RECEIPT_METRIC_REQUIRED");
        approvalId = required(approvalId, "UPDATE_ALERT_THRESHOLD_RECEIPT_APPROVAL_REQUIRED");
        actor = required(actor, "UPDATE_ALERT_THRESHOLD_RECEIPT_ACTOR_REQUIRED");
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_EXPECTED_VERSION_INVALID");
        }
        if (hashVersion != UpdateAlertThresholdReceiptHashing.HASH_VERSION) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_HASH_VERSION_UNSUPPORTED");
        }
        operationInputHash = required(
                operationInputHash,
                "UPDATE_ALERT_THRESHOLD_RECEIPT_INPUT_HASH_REQUIRED");
        if (!INPUT_HASH.matcher(operationInputHash).matches()) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_INPUT_HASH_INVALID");
        }
        if (previousValue == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_PREVIOUS_VALUE_REQUIRED");
        }
        if (currentValue == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_CURRENT_VALUE_REQUIRED");
        }
        if (currentVersion <= 0L) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_VERSION_INVALID");
        }
        resultHash = required(resultHash, "UPDATE_ALERT_THRESHOLD_RECEIPT_RESULT_HASH_REQUIRED");
        if (!RESULT_HASH.matcher(resultHash).matches()) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_RESULT_HASH_INVALID");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_COMPLETED_AT_REQUIRED");
        }
    }

    public static UpdateAlertThresholdReceipt from(Map<String, Object> output) {
        Map<String, Object> source = unwrap(output);
        return new UpdateAlertThresholdReceipt(
                text(source.get("status")),
                first(source.get("receiptId"), source.get("receipt_id")),
                first(source.get("operation"), source.get("toolName"), source.get("tool_name")),
                first(source.get("executionKey"), source.get("execution_key")),
                first(source.get("projectId"), source.get("project_id")),
                text(source.get("metric")),
                first(source.get("approvalId"), source.get("approval_id")),
                text(source.get("actor")),
                longValue(firstObject(source.get("expectedVersion"), source.get("expected_version")),
                        "UPDATE_ALERT_THRESHOLD_RECEIPT_EXPECTED_VERSION_INVALID"),
                intValue(firstObject(source.get("hashVersion"), source.get("hash_version")),
                        "UPDATE_ALERT_THRESHOLD_RECEIPT_HASH_VERSION_UNSUPPORTED"),
                first(source.get("operationInputHash"), source.get("operation_input_hash")),
                decimal(firstObject(source.get("previousValue"), source.get("previous_value")),
                        "UPDATE_ALERT_THRESHOLD_RECEIPT_PREVIOUS_VALUE_REQUIRED"),
                decimal(firstObject(source.get("currentValue"), source.get("current_value")),
                        "UPDATE_ALERT_THRESHOLD_RECEIPT_CURRENT_VALUE_REQUIRED"),
                longValue(firstObject(source.get("currentVersion"), source.get("current_version")),
                        "UPDATE_ALERT_THRESHOLD_RECEIPT_VERSION_INVALID"),
                first(source.get("resultHash"), source.get("result_hash"), source.get("outputHash")),
                instant(firstObject(source.get("completedAt"), source.get("completed_at"))));
    }

    public void verify(UpdateAlertThresholdCommand command) {
        verify(command, "");
    }

    public void verify(UpdateAlertThresholdCommand command, String expectedOperation) {
        if (command == null) throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_COMMAND_REQUIRED");
        String normalizedOperation = text(expectedOperation);
        if (!normalizedOperation.isBlank() && !operation.equals(normalizedOperation)) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_OPERATION_MISMATCH");
        }
        if (!executionKey.equals(command.executionKey())) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_EXECUTION_KEY_MISMATCH");
        }
        if (!projectId.equals(command.projectId())) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_PROJECT_MISMATCH");
        }
        if (!metric.equals(command.metric())) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_METRIC_MISMATCH");
        }
        if (!approvalId.equals(command.approvalId())) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_APPROVAL_MISMATCH");
        }
        if (!actor.equals(command.actor())) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_ACTOR_MISMATCH");
        }
        if (expectedVersion != command.expectedVersion()) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_EXPECTED_VERSION_MISMATCH");
        }
        String expectedInputHash = UpdateAlertThresholdReceiptHashing.operationInputHash(
                command,
                operation);
        if (!operationInputHash.equalsIgnoreCase(expectedInputHash)) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_INPUT_HASH_MISMATCH");
        }
        if (previousValue.compareTo(command.expectedValue()) != 0) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_PREVIOUS_VALUE_MISMATCH");
        }
        if (currentValue.compareTo(command.newValue()) != 0) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_CURRENT_VALUE_MISMATCH");
        }
        if (currentVersion != command.expectedVersion() + 1L) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_VERSION_MISMATCH");
        }
        String expectedResultHash = UpdateAlertThresholdReceiptHashing.resultHash(this);
        if (!UpdateAlertThresholdReceiptHashing.sameResultHash(resultHash, expectedResultHash)) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_RESULT_HASH_MISMATCH");
        }
    }

    private static Map<String, Object> unwrap(Map<String, Object> output) {
        Map<String, Object> current = output == null ? Map.of() : output;
        for (String key : new String[]{"providerResult", "payload", "data", "result", "output"}) {
            Object nested = current.get(key);
            if (nested instanceof Map<?, ?> map && hasReceiptFacts(map)) {
                java.util.LinkedHashMap<String, Object> typed = new java.util.LinkedHashMap<>();
                map.forEach((itemKey, value) -> typed.put(String.valueOf(itemKey), value));
                return Map.copyOf(typed);
            }
        }
        return current;
    }

    private static boolean hasReceiptFacts(Map<?, ?> map) {
        return map.containsKey("receiptId")
                || map.containsKey("receipt_id")
                || map.containsKey("currentVersion")
                || map.containsKey("current_version");
    }

    private static BigDecimal decimal(Object value, String reasonCode) {
        try {
            String normalized = text(value);
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return new BigDecimal(normalized).stripTrailingZeros();
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

    private static int intValue(Object value, String reasonCode) {
        try {
            String normalized = text(value);
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return Integer.parseInt(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(reasonCode, error);
        }
    }

    private static Instant instant(Object value) {
        try {
            String normalized = text(value);
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_COMPLETED_AT_REQUIRED");
            }
            return Instant.parse(normalized);
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal
                    && "UPDATE_ALERT_THRESHOLD_RECEIPT_COMPLETED_AT_REQUIRED".equals(illegal.getMessage())) {
                throw illegal;
            }
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_COMPLETED_AT_INVALID", error);
        }
    }

    private static Object firstObject(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static String first(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
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
