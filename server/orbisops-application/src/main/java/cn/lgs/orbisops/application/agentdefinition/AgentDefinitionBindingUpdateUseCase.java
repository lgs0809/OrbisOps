package cn.lgs.orbisops.application.agentdefinition;

/** Application use case for saving one new Agent Definition draft with updated capability bindings. */
public final class AgentDefinitionBindingUpdateUseCase<D, B, V> {

    private final AgentDefinitionLifecycleUseCase<D> lifecycleUseCase;
    private final AgentDefinitionBindingUpdatePort<D, B, V> updatePort;
    private final AgentDefinitionBindingUpdateAuditPort<D, V> auditPort;

    public AgentDefinitionBindingUpdateUseCase(
            AgentDefinitionLifecycleUseCase<D> lifecycleUseCase,
            AgentDefinitionBindingUpdatePort<D, B, V> updatePort,
            AgentDefinitionBindingUpdateAuditPort<D, V> auditPort) {
        if (lifecycleUseCase == null || updatePort == null || auditPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_BINDING_UPDATE_DEPENDENCIES_REQUIRED");
        }
        this.lifecycleUseCase = lifecycleUseCase;
        this.updatePort = updatePort;
        this.auditPort = auditPort;
    }

    public AgentDefinitionBindingUpdateResult<D, V> update(
            AgentDefinitionBindingUpdateCommand<B> command) {
        if (command == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_BINDING_UPDATE_COMMAND_REQUIRED");
        }
        D definition = updatePort.resolveDraft(command.agentId());
        if (definition == null) {
            throw new IllegalArgumentException("Agent 不存在：" + command.agentId());
        }
        D updated = updatePort.applyBindings(definition, command.bindings());
        updatePort.assertProjectAndBindingsValid(updated);
        D normalized = updatePort.normalizeExecutionShape(updated);
        D saved = lifecycleUseCase.saveDraft(normalized);
        V effectiveBindings = updatePort.effectiveBindings(saved);
        auditPort.recordBindingUpdate(command.agentId(), saved, effectiveBindings);
        return new AgentDefinitionBindingUpdateResult<>(saved, effectiveBindings);
    }
}
