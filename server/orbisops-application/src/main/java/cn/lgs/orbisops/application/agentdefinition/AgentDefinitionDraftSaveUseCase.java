package cn.lgs.orbisops.application.agentdefinition;

/** Application use case for validated and normalized Agent Definition draft persistence. */
public final class AgentDefinitionDraftSaveUseCase<D> {

    private final AgentDefinitionLifecycleUseCase<D> lifecycleUseCase;
    private final AgentDefinitionDraftSavePort<D> draftPort;
    private final AgentDefinitionDraftSaveAuditPort<D> auditPort;

    public AgentDefinitionDraftSaveUseCase(
            AgentDefinitionLifecycleUseCase<D> lifecycleUseCase,
            AgentDefinitionDraftSavePort<D> draftPort,
            AgentDefinitionDraftSaveAuditPort<D> auditPort) {
        if (lifecycleUseCase == null || draftPort == null || auditPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_DRAFT_SAVE_DEPENDENCIES_REQUIRED");
        }
        this.lifecycleUseCase = lifecycleUseCase;
        this.draftPort = draftPort;
        this.auditPort = auditPort;
    }

    public D save(AgentDefinitionDraftSaveCommand<D> command) {
        if (command == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_DRAFT_SAVE_COMMAND_REQUIRED");
        }
        draftPort.assertProjectAndBindingsValid(command.definition());
        D normalized = draftPort.normalizeExecutionShape(command.definition());
        D saved = lifecycleUseCase.saveDraft(normalized);
        auditPort.recordDraftSave(command.auditAction(), saved);
        return saved;
    }
}
