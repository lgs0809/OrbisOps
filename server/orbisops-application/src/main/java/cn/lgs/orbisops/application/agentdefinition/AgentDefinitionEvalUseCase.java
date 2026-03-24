package cn.lgs.orbisops.application.agentdefinition;

/** Application use case for project-admitted Agent evaluation suite creation and execution. */
public final class AgentDefinitionEvalUseCase<R, O> {

    private final AgentDefinitionEvalPort<R, O> evalPort;

    public AgentDefinitionEvalUseCase(AgentDefinitionEvalPort<R, O> evalPort) {
        if (evalPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_EVAL_PORT_REQUIRED");
        }
        this.evalPort = evalPort;
    }

    public O createSuite(AgentDefinitionEvalSuiteCommand<R> command) {
        if (command == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_EVAL_SUITE_COMMAND_REQUIRED");
        }
        String projectId = evalPort.requireExistingProject(command.projectId());
        return evalPort.createSuite(
                projectId,
                command.agentId(),
                command.request(),
                command.actor());
    }

    public O run(AgentDefinitionEvalRunCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_EVAL_RUN_COMMAND_REQUIRED");
        }
        String projectId = evalPort.requireExistingProject(command.projectId());
        return evalPort.run(
                projectId,
                command.agentId(),
                command.version(),
                command.suiteId(),
                command.actor());
    }
}
