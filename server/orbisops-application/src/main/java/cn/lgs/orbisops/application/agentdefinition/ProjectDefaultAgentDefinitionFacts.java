package cn.lgs.orbisops.application.agentdefinition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Protocol-neutral facts required by the default Agent bootstrap process. */
public record ProjectDefaultAgentDefinitionFacts(
        String agentId,
        String projectId,
        Integer version,
        String lifecycle,
        String definitionHash,
        Set<String> agentScopeRoles) {

    public ProjectDefaultAgentDefinitionFacts {
        agentId = text(agentId);
        projectId = text(projectId);
        lifecycle = text(lifecycle);
        definitionHash = text(definitionHash);
        agentScopeRoles = agentScopeRoles == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(agentScopeRoles));
    }

    public int requireVersion() {
        if (version == null || version <= 0) {
            throw new IllegalStateException("AGENT_DEFINITION_VERSION_INVALID");
        }
        return version;
    }

    public boolean publishedFor(String expectedProjectId) {
        return expectedProjectId != null
                && expectedProjectId.trim().equals(projectId)
                && "PUBLISHED".equalsIgnoreCase(lifecycle);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
