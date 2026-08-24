package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Application query use case for project-scoped Agent Definitions and effective bindings. */
public final class AgentDefinitionQueryUseCase<D, B> {

    private final AgentDefinitionQueryPort<D, B> queryPort;

    public AgentDefinitionQueryUseCase(AgentDefinitionQueryPort<D, B> queryPort) {
        if (queryPort == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_QUERY_PORT_REQUIRED");
        }
        this.queryPort = queryPort;
    }

    public List<D> listProjectAgents() {
        List<D> values = queryPort.listAll();
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(queryPort::projectScoped)
                .toList();
    }

    public List<D> listProjectAgents(String projectId) {
        String normalized = required(
                projectId,
                "查询 Agent 必须提供 projectId");
        List<D> values = queryPort.listForProject(normalized);
        return values == null ? List.of() : List.copyOf(values);
    }

    public D find(String agentId) {
        String normalized = text(agentId);
        if (normalized.isBlank()) return null;
        // The admin editor must reopen an unpublished draft. Runtime resolution still uses its published pointer.
        List<D> versions = queryPort.listVersions(normalized);
        if (versions != null && !versions.isEmpty()) return versions.get(0);
        List<D> values = queryPort.listAll();
        if (values == null || values.isEmpty()) return null;
        return values.stream()
                .filter(value -> normalized.equals(queryPort.agentId(value)))
                .findFirst()
                .orElse(null);
    }

    public List<D> versions(String agentId) {
        List<D> values = queryPort.listVersions(text(agentId));
        return values == null ? List.of() : List.copyOf(values);
    }

    public List<B> bindings(String agentId) {
        String normalized = required(
                agentId,
                "查询 Agent 绑定必须提供 agentId");
        List<B> stored = queryPort.storedBindings(normalized);
        if (stored != null && !stored.isEmpty()) {
            return List.copyOf(stored);
        }
        D definition = queryPort.resolveDraft(normalized);
        List<B> derived = queryPort.deriveBindings(definition);
        return derived == null ? List.of() : List.copyOf(derived);
    }

    private String required(String value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
