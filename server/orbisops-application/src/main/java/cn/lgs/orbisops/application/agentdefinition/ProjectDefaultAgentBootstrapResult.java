package cn.lgs.orbisops.application.agentdefinition;

/** Result of ensuring one project's default operations Agent. */
public record ProjectDefaultAgentBootstrapResult<D>(
        D definition,
        boolean reusedPublishedDefinition,
        String action,
        String suiteId,
        String evalRunId) {

    public ProjectDefaultAgentBootstrapResult {
        if (definition == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_DEFINITION_REQUIRED");
        }
        action = text(action);
        suiteId = text(suiteId);
        evalRunId = text(evalRunId);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
