package cn.lgs.orbisops.application.agentdefinition;

/** Application process manager for audited Agent Definition version lifecycle operations. */
public final class AgentDefinitionLifecycleOperationUseCase<D, S> {

    private final AgentDefinitionLifecycleUseCase<D> lifecycleUseCase;
    private final AgentDefinitionLifecycleOperationPort<S> operationPort;
    private final AgentDefinitionLifecycleOperationAuditPort<D, S> auditPort;

    public AgentDefinitionLifecycleOperationUseCase(
            AgentDefinitionLifecycleUseCase<D> lifecycleUseCase,
            AgentDefinitionLifecycleOperationPort<S> operationPort,
            AgentDefinitionLifecycleOperationAuditPort<D, S> auditPort) {
        if (lifecycleUseCase == null || operationPort == null || auditPort == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_LIFECYCLE_OPERATION_DEPENDENCIES_REQUIRED");
        }
        this.lifecycleUseCase = lifecycleUseCase;
        this.operationPort = operationPort;
        this.auditPort = auditPort;
    }

    public D validate(AgentDefinitionVersionCommand command) {
        AgentDefinitionVersionCommand required = required(command);
        D result = lifecycleUseCase.validate(required.agentId(), required.version());
        auditPort.recordTransition(
                "validate",
                required.agentId(),
                required.version(),
                result);
        return result;
    }

    public D publish(AgentDefinitionVersionCommand command) {
        AgentDefinitionVersionCommand required = required(command);
        D result = lifecycleUseCase.publish(required.agentId(), required.version());
        auditPort.recordTransition(
                "publish",
                required.agentId(),
                required.version(),
                result);
        return result;
    }

    public D rollback(AgentDefinitionVersionCommand command) {
        AgentDefinitionVersionCommand required = required(command);
        D result = lifecycleUseCase.rollback(required.agentId(), required.version());
        auditPort.recordTransition(
                "rollback",
                required.agentId(),
                required.version(),
                result);
        return result;
    }

    public boolean disable(AgentDefinitionVersionCommand command) {
        AgentDefinitionVersionCommand required = required(command);
        S before = operationPort.currentSnapshot(required.agentId());
        boolean disabled = lifecycleUseCase.disable(required.agentId(), required.version());
        auditPort.recordDisable(
                required.agentId(),
                required.version(),
                before,
                disabled);
        return disabled;
    }

    private AgentDefinitionVersionCommand required(AgentDefinitionVersionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VERSION_COMMAND_REQUIRED");
        }
        return command;
    }
}
