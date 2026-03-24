package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Application request for sanitizing every capability owner in one Agent Definition. */
public record AgentCapabilitySanitizationRequest(
        String projectId,
        List<AgentCapabilityOwnerSelection> selections) {

    public AgentCapabilitySanitizationRequest {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_PROJECT_ID_REQUIRED");
        }
        projectId = projectId.trim();
        selections = selections == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(selections));
    }
}
