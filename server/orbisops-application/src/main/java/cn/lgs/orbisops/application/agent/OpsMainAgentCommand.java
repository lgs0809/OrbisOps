package cn.lgs.orbisops.application.agent;

import java.util.Map;
import java.util.Objects;

public record OpsMainAgentCommand(
        OpsMainAgentActionType actionType,
        String runId,
        String sessionId,
        String projectId,
        String actorId,
        String userInput,
        OpsMainAgentPayload payload,
        Map<String, Object> attributes) {

    public OpsMainAgentCommand {
        actionType = Objects.requireNonNull(actionType, "actionType");
        runId = text(runId);
        sessionId = text(sessionId);
        projectId = text(projectId);
        actorId = text(actorId);
        userInput = text(userInput);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
