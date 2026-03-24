package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Domain decision for one built-in sub-agent's depth and runtime tool boundary. */
public record SubAgentToolBoundaryDecision(
        boolean active,
        String role,
        int maxDepth,
        List<String> allowedToolPatterns,
        List<String> permittedToolNames) {

    public SubAgentToolBoundaryDecision {
        role = role == null ? "" : role.trim();
        maxDepth = Math.max(0, maxDepth);
        allowedToolPatterns = immutable(allowedToolPatterns);
        permittedToolNames = immutable(permittedToolNames);
    }

    public static SubAgentToolBoundaryDecision inactive() {
        return new SubAgentToolBoundaryDecision(
                false,
                "",
                0,
                List.of(),
                List.of());
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
