package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;

/** Final first-match resolution, including all attempted source facts. */
public record OpsMcpRuntimeConfigResolution(
        OpsMcpRuntimeConfigRequest request,
        OpsMcpServerConfig config,
        String selectedSourceId,
        OpsMcpRuntimeConfigSourceResult.Outcome terminalOutcome,
        String terminalReason,
        List<OpsMcpRuntimeConfigSourceAttempt> attempts
) {

    public OpsMcpRuntimeConfigResolution {
        if (request == null) throw new IllegalArgumentException("MCP_CONFIG_RESOLUTION_REQUEST_REQUIRED");
        selectedSourceId = text(selectedSourceId);
        if (terminalOutcome == null) {
            throw new IllegalArgumentException("MCP_CONFIG_RESOLUTION_OUTCOME_REQUIRED");
        }
        terminalReason = text(terminalReason);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        if (attempts.isEmpty()) throw new IllegalArgumentException("MCP_CONFIG_RESOLUTION_ATTEMPTS_REQUIRED");
        if (terminalOutcome == OpsMcpRuntimeConfigSourceResult.Outcome.MATCH) {
            if (config == null || selectedSourceId.isBlank()) {
                throw new IllegalArgumentException("MCP_CONFIG_RESOLUTION_MATCH_IDENTITY_REQUIRED");
            }
        } else if (config != null) {
            throw new IllegalArgumentException("MCP_CONFIG_RESOLUTION_NON_MATCH_CONFIG_FORBIDDEN");
        }
    }

    public boolean matched() {
        return terminalOutcome == OpsMcpRuntimeConfigSourceResult.Outcome.MATCH;
    }

    public boolean blocked() {
        return terminalOutcome == OpsMcpRuntimeConfigSourceResult.Outcome.BLOCKED;
    }

    public boolean fallback() {
        return attempts.size() > 1;
    }

    public String fallbackReason() {
        if (!fallback()) return "";
        return attempts.subList(0, attempts.size() - 1).stream()
                .map(attempt -> attempt.sourceId() + ":" + attempt.outcome() + ":" + attempt.reason())
                .reduce((left, right) -> left + " -> " + right)
                .orElse("");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
