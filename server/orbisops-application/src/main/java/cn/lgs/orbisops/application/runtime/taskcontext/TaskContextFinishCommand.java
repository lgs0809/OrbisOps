package cn.lgs.orbisops.application.runtime.taskcontext;

import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextEventFact;

import java.util.List;

public record TaskContextFinishCommand(
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        String goal,
        List<String> usedSkills,
        String status,
        String output,
        List<TaskContextEventFact> events) {

    public TaskContextFinishCommand {
        usedSkills = usedSkills == null ? List.of() : List.copyOf(usedSkills);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
