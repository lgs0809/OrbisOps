package cn.lgs.orbisops.application.agentdefinition;

/** Command for creating an evaluation suite through the Agent Definition management boundary. */
public record AgentDefinitionEvalSuiteCommand<R>(
        String projectId,
        String agentId,
        R request,
        String actor) {

    public AgentDefinitionEvalSuiteCommand {
        projectId = text(projectId);
        agentId = text(agentId);
        actor = text(actor);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
