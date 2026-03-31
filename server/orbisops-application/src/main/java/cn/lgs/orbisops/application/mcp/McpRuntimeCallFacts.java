package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;

import java.util.Map;
import java.util.Optional;

/** Typed call-level facts decoded from the runtime adapter metadata envelope. */
public record McpRuntimeCallFacts(
        Optional<McpRiskLevel> riskLevel,
        Optional<Boolean> readOnly
) {

    public McpRuntimeCallFacts {
        riskLevel = riskLevel == null ? Optional.empty() : riskLevel;
        readOnly = readOnly == null ? Optional.empty() : readOnly;
    }

    public static McpRuntimeCallFacts from(Map<String, Object> metadata) {
        Map<String, Object> safe = metadata == null ? Map.of() : metadata;
        return new McpRuntimeCallFacts(
                optionalRisk(safe.get("riskLevel")),
                optionalBoolean(safe.get("readOnly")));
    }

    private static Optional<McpRiskLevel> optionalRisk(Object value) {
        String normalized = text(value);
        return normalized.isBlank()
                ? Optional.empty()
                : Optional.of(McpRiskLevel.failClosed(normalized));
    }

    private static Optional<Boolean> optionalBoolean(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof Boolean bool) return Optional.of(bool);
        if (value instanceof Number number) return Optional.of(number.intValue() != 0);
        String normalized = text(value);
        if (normalized.isBlank()) return Optional.empty();
        return Optional.of("true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "y".equalsIgnoreCase(normalized));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
