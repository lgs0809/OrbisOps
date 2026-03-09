package cn.lgs.orbisops.application.agentdefinition;

/** Application use case for cloning one Agent Definition into another project. */
public final class AgentDefinitionCloneUseCase<D> {

    private final AgentDefinitionLifecycleUseCase<D> lifecycleUseCase;
    private final AgentDefinitionClonePort<D> clonePort;
    private final AgentDefinitionCloneAuditPort<D> auditPort;

    public AgentDefinitionCloneUseCase(
            AgentDefinitionLifecycleUseCase<D> lifecycleUseCase,
            AgentDefinitionClonePort<D> clonePort,
            AgentDefinitionCloneAuditPort<D> auditPort) {
        if (lifecycleUseCase == null || clonePort == null || auditPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_CLONE_DEPENDENCIES_REQUIRED");
        }
        this.lifecycleUseCase = lifecycleUseCase;
        this.clonePort = clonePort;
        this.auditPort = auditPort;
    }

    public D clone(AgentDefinitionCloneCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_CLONE_COMMAND_REQUIRED");
        }
        String projectId = clonePort.requireExistingProject(command.targetProjectId());
        D source = clonePort.resolveSource(command.sourceAgentId());
        if (source == null) {
            throw new IllegalArgumentException("源 Agent 不存在：" + command.sourceAgentId());
        }
        D sanitized = clonePort.sanitizeForProject(source, projectId);
        D prepared = clonePort.prepareClone(
                sanitized,
                projectId,
                command.newAgentId(),
                command.newName());
        D normalized = clonePort.normalizeExecutionShape(prepared);
        D saved = lifecycleUseCase.saveDraft(normalized);
        auditPort.recordClone(
                command.sourceAgentId(),
                command.newAgentId(),
                saved);
        return saved;
    }
}
