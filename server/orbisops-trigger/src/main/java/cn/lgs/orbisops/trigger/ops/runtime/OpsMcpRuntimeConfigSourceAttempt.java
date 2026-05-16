package cn.lgs.orbisops.trigger.ops.runtime;

/** Immutable audit fact for one ordered MCP runtime config source attempt. */
public record OpsMcpRuntimeConfigSourceAttempt(
        String sourceId,
        OpsMcpRuntimeConfigSourceResult.Outcome outcome,
        String reason
) {

    public OpsMcpRuntimeConfigSourceAttempt {
        sourceId = required(sourceId, "MCP_CONFIG_SOURCE_ID_REQUIRED");
        if (outcome == null) throw new IllegalArgumentException("MCP_CONFIG_SOURCE_OUTCOME_REQUIRED");
        reason = reason == null ? "" : reason.trim();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
