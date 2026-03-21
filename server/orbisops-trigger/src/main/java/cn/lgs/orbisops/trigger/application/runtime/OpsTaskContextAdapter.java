package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextCommandApplicationService;
import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextQueryApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpsTaskContextAdapter {

    private final TaskContextCommandApplicationService commands;
    private final TaskContextQueryApplicationService queries;
    private final OpsTaskContextMapper mapper;

    public OpsTaskContextAdapter(
            TaskContextCommandApplicationService commands,
            TaskContextQueryApplicationService queries,
            OpsTaskContextMapper mapper) {
        if (commands == null) throw new IllegalArgumentException("TASK_CONTEXT_COMMANDS_REQUIRED");
        if (queries == null) throw new IllegalArgumentException("TASK_CONTEXT_QUERIES_REQUIRED");
        if (mapper == null) throw new IllegalArgumentException("TASK_CONTEXT_MAPPER_REQUIRED");
        this.commands = commands;
        this.queries = queries;
        this.mapper = mapper;
    }

    public void startRun(OpsAgentChatRequest request, OpsAgentDefinition definition) {
        commands.start(mapper.start(request, definition));
    }

    public void updateFromEvents(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            String taskState,
            List<OpsRuntimeEvent> events,
            String summary) {
        commands.progress(mapper.progress(request, definition, taskState, events, summary));
    }

    public void finishRun(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            String status,
            String output,
            List<OpsRuntimeEvent> events) {
        commands.finish(mapper.finish(request, definition, status, output, events));
    }

    public Map<String, Object> get(String runId) {
        return mapper.view(queries.get(runId));
    }
}
