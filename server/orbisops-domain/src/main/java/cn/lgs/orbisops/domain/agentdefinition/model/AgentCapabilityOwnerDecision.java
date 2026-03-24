package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Authorized capability selection returned for one Agent Definition owner. */
public record AgentCapabilityOwnerDecision(
        String ownerKey,
        String knowledgeBaseId,
        List<String> skillIds,
        List<String> projectToolIds,
        List<String> executionTargetIds,
        boolean forceRagEnabled) {

    public AgentCapabilityOwnerDecision {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_OWNER_KEY_REQUIRED");
        }
        ownerKey = ownerKey.trim();
        knowledgeBaseId = knowledgeBaseId == null ? "" : knowledgeBaseId.trim();
        skillIds = immutable(skillIds);
        projectToolIds = immutable(projectToolIds);
        executionTargetIds = immutable(executionTargetIds);
    }

    private static List<String> immutable(List<String> values) {
        return values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
