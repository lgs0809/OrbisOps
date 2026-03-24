package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Application process manager for audited Agent Definition deletion and reload. */
public final class AgentDefinitionAdministrationUseCase<D, S> {

    private final AgentDefinitionAdministrationPort<D, S> administrationPort;
    private final AgentDefinitionAdministrationAuditPort<S> auditPort;

    public AgentDefinitionAdministrationUseCase(
            AgentDefinitionAdministrationPort<D, S> administrationPort,
            AgentDefinitionAdministrationAuditPort<S> auditPort) {
        if (administrationPort == null || auditPort == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_ADMINISTRATION_DEPENDENCIES_REQUIRED");
        }
        this.administrationPort = administrationPort;
        this.auditPort = auditPort;
    }

    public boolean delete(String agentId) {
        String normalized = text(agentId);
        S before = administrationPort.currentSnapshot(normalized);
        boolean deleted = administrationPort.delete(normalized);
        auditPort.recordDelete(normalized, before, deleted);
        return deleted;
    }

    public List<D> reload() {
        administrationPort.reload();
        auditPort.recordReload();
        List<D> definitions = administrationPort.listProjectDefinitions();
        return definitions == null ? List.of() : List.copyOf(definitions);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
