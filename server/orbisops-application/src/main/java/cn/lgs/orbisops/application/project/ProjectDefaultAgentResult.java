package cn.lgs.orbisops.application.project;

/** Stable result of ensuring a project's default Agent. */
public record ProjectDefaultAgentResult(String agentId) {

    public ProjectDefaultAgentResult {
        agentId = required(agentId);
    }

    private static String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_REQUIRED");
        return normalized;
    }
}
