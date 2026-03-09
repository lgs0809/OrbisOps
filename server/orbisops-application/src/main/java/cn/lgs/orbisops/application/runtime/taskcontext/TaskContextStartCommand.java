package cn.lgs.orbisops.application.runtime.taskcontext;

import java.util.List;

public record TaskContextStartCommand(
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        String goal,
        List<String> usedSkills) {

    public TaskContextStartCommand {
        usedSkills = usedSkills == null ? List.of() : List.copyOf(usedSkills);
    }
}
