package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Requested capabilities of one Agent, Node or AgentScope owner before project sanitization. */
public record AgentCapabilityOwnerSelection(
        String ownerKey,
        List<String> requestedSkills,
        List<String> requestedProjectTools,
        List<String> requestedExecutionTargets,
        boolean enableRagWhenNoProjectTool) {

    public AgentCapabilityOwnerSelection {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_OWNER_KEY_REQUIRED");
        }
        ownerKey = ownerKey.trim();
        requestedSkills = immutable(requestedSkills);
        requestedProjectTools = immutable(requestedProjectTools);
        requestedExecutionTargets = immutable(requestedExecutionTargets);
    }

    private static List<String> immutable(List<String> values) {
        return values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
