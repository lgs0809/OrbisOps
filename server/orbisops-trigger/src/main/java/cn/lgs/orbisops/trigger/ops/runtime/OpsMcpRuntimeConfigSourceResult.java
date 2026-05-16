package cn.lgs.orbisops.trigger.ops.runtime;

/** One ordered MCP config source attempt. */
public record OpsMcpRuntimeConfigSourceResult(
        Outcome outcome,
        OpsMcpServerConfig config,
        String reason
) {

    public enum Outcome {
        MATCH,
        MISS,
        UNAVAILABLE,
        BLOCKED
    }

    public OpsMcpRuntimeConfigSourceResult {
        if (outcome == null) throw new IllegalArgumentException("MCP_CONFIG_SOURCE_OUTCOME_REQUIRED");
        reason = reason == null ? "" : reason.trim();
        if (outcome == Outcome.MATCH && config == null) {
            throw new IllegalArgumentException("MCP_CONFIG_SOURCE_MATCH_CONFIG_REQUIRED");
        }
        if (outcome != Outcome.MATCH && config != null) {
            throw new IllegalArgumentException("MCP_CONFIG_SOURCE_NON_MATCH_CONFIG_FORBIDDEN");
        }
    }

    public static OpsMcpRuntimeConfigSourceResult match(OpsMcpServerConfig config) {
        return new OpsMcpRuntimeConfigSourceResult(Outcome.MATCH, config, "MATCHED");
    }

    public static OpsMcpRuntimeConfigSourceResult miss(String reason) {
        return new OpsMcpRuntimeConfigSourceResult(Outcome.MISS, null, reason);
    }

    public static OpsMcpRuntimeConfigSourceResult unavailable(String reason) {
        return new OpsMcpRuntimeConfigSourceResult(Outcome.UNAVAILABLE, null, reason);
    }

    public static OpsMcpRuntimeConfigSourceResult blocked(String reason) {
        return new OpsMcpRuntimeConfigSourceResult(Outcome.BLOCKED, null, reason);
    }
}
