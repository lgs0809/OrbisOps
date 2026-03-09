package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Protocol-neutral AgentScope sub-agent definitions owned by one Agent. */
public record AgentScopeDefinition(List<Scope> scopes) {

    public AgentScopeDefinition {
        scopes = scopes == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(scopes));
    }

    public record Scope(
            String agentId,
            String name,
            String instruction,
            Integer maxDepth,
            String role,
            List<String> allowedToolNames) {

        public Scope {
            agentId = text(agentId);
            name = text(name);
            instruction = text(instruction);
            role = text(role);
            allowedToolNames = allowedToolNames == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(allowedToolNames));
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
