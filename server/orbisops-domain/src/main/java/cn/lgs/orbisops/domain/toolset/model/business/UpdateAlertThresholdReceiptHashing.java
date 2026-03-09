package cn.lgs.orbisops.domain.toolset.model.business;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HexFormat;

/**
 * Cross-language canonical SHA-256 contract for the controlled alert-threshold Tool.
 * Numeric business values are hashed as canonical decimal strings so Java and
 * JavaScript do not diverge because of floating-point or exponent formatting.
 */
public final class UpdateAlertThresholdReceiptHashing {

    public static final int HASH_VERSION = 1;

    private static final DateTimeFormatter PROVIDER_INSTANT =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private UpdateAlertThresholdReceiptHashing() {
    }

    public static String operationInputHash(
            UpdateAlertThresholdCommand command,
            String operation) {
        if (command == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_COMMAND_REQUIRED");
        }
        String normalizedOperation = required(
                operation,
                "UPDATE_ALERT_THRESHOLD_RECEIPT_OPERATION_REQUIRED");
        String canonical = "{"
                + property("actor", command.actor()) + ","
                + property("approvalId", command.approvalId()) + ","
                + property("deadline", command.deadline().toString()) + ","
                + property("executionKey", command.executionKey()) + ","
                + property("expectedValue", decimal(command.expectedValue())) + ","
                + property("expectedVersion", String.valueOf(command.expectedVersion())) + ","
                + rawProperty("hashVersion", String.valueOf(HASH_VERSION)) + ","
                + property("metric", command.metric()) + ","
                + property("newValue", decimal(command.newValue())) + ","
                + property("projectId", command.projectId()) + ","
                + property("toolName", normalizedOperation)
                + "}";
        return sha256(canonical);
    }

    public static String resultHash(UpdateAlertThresholdReceipt receipt) {
        if (receipt == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_REQUIRED");
        }
        String canonical = "{"
                + property("actor", receipt.actor()) + ","
                + property("approvalId", receipt.approvalId()) + ","
                + property("completedAt", PROVIDER_INSTANT.format(receipt.completedAt())) + ","
                + property("currentValue", decimal(receipt.currentValue())) + ","
                + property("currentVersion", String.valueOf(receipt.currentVersion())) + ","
                + property("executionKey", receipt.executionKey()) + ","
                + property("expectedVersion", String.valueOf(receipt.expectedVersion())) + ","
                + rawProperty("hashVersion", String.valueOf(receipt.hashVersion())) + ","
                + property("metric", receipt.metric()) + ","
                + property("operation", receipt.operation()) + ","
                + property("operationInputHash", receipt.operationInputHash()) + ","
                + property("previousValue", decimal(receipt.previousValue())) + ","
                + property("projectId", receipt.projectId()) + ","
                + property("receiptId", receipt.receiptId()) + ","
                + property("status", receipt.status())
                + "}";
        return "sha256:" + sha256(canonical);
    }

    public static boolean sameResultHash(String left, String right) {
        return normalizeResultHash(left).equalsIgnoreCase(normalizeResultHash(right));
    }

    private static String normalizeResultHash(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.regionMatches(true, 0, "sha256:", 0, 7)
                ? normalized.substring(7)
                : normalized;
    }

    private static String decimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_HASH_DECIMAL_REQUIRED");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String property(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    private static String rawProperty(String name, String value) {
        return quote(name) + ":" + value;
    }

    private static String quote(String value) {
        String source = value == null ? "" : value;
        StringBuilder result = new StringBuilder(source.length() + 2);
        result.append('"');
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character <= 0x1F || isUnpairedSurrogate(source, index, character)) {
                        result.append("\\u");
                        appendHex(result, character);
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        result.append('"');
        return result.toString();
    }

    private static boolean isUnpairedSurrogate(
            String source,
            int index,
            char character) {
        if (Character.isHighSurrogate(character)) {
            return index + 1 >= source.length()
                    || !Character.isLowSurrogate(source.charAt(index + 1));
        }
        if (Character.isLowSurrogate(character)) {
            return index == 0 || !Character.isHighSurrogate(source.charAt(index - 1));
        }
        return false;
    }

    private static void appendHex(StringBuilder target, char value) {
        String hex = Integer.toHexString(value);
        target.append("0".repeat(4 - hex.length())).append(hex);
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
