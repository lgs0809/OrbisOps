package cn.lgs.orbisops.application.agentdefinition;

/** Command for running one Agent Definition evaluation suite. */
public record AgentDefinitionEvalRunCommand(
        String projectId,
        String agentId,
        int version,
        String suiteId,
        String actor) {

    public AgentDefinitionEvalRunCommand(
            String projectId,
            String agentId,
            Integer version,
            String suiteId,
            String actor) {
        this(
                text(projectId),
                text(agentId),
                requireVersion(version),
                text(suiteId),
                text(actor));
    }

    public AgentDefinitionEvalRunCommand {
        projectId = text(projectId);
        agentId = text(agentId);
        suiteId = text(suiteId);
        actor = text(actor);
        if (version <= 0) throw new IllegalArgumentException("version 必须大于 0");
    }

    private static int requireVersion(Integer version) {
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("version 必须大于 0");
        }
        return version;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
