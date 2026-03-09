package cn.lgs.orbisops.application.agentdefinition;

/** Command for one validated Agent Definition draft save, including its audit compatibility action. */
public record AgentDefinitionDraftSaveCommand<D>(
        D definition,
        String auditAction) {

    public AgentDefinitionDraftSaveCommand {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        }
        auditAction = auditAction == null ? "" : auditAction.trim();
        if (auditAction.isBlank()) {
            throw new IllegalArgumentException("AGENT_DEFINITION_DRAFT_AUDIT_ACTION_REQUIRED");
        }
    }
}
