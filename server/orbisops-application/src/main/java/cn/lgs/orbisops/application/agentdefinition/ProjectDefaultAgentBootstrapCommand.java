package cn.lgs.orbisops.application.agentdefinition;

/** Command for ensuring that one project owns a released default operations Agent. */
public record ProjectDefaultAgentBootstrapCommand(
        String projectId,
        String projectName,
        String actor) {

    public ProjectDefaultAgentBootstrapCommand {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_PROJECT_ID_REQUIRED");
        }
        projectId = projectId.trim();
        projectName = projectName == null ? "" : projectName.trim();
        actor = actor == null || actor.isBlank()
                ? "SYSTEM_DEFAULT_AGENT_BOOTSTRAP"
                : actor.trim();
    }

    public String agentId() {
        return projectId + "-ops-agent";
    }

    public String agentName() {
        return (projectName.isBlank() ? projectId : projectName) + "运维 Agent";
    }
}
