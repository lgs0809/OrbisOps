package cn.lgs.orbisops.application.runtime.taskcontext;

import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextEventFact;

import java.util.List;

public record TaskContextProgressCommand(
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        String goal,
        List<String> usedSkills,
        String taskState,
        List<TaskContextEventFact> events,
        String summary) {

    public TaskContextProgressCommand {
        usedSkills = usedSkills == null ? List.of() : List.copyOf(usedSkills);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
