package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextFinishCommand;
import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextProgressCommand;
import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextStartCommand;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextContent;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextEventFact;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsTaskContextMapper {

    private static final DateTimeFormatter DB_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TaskContextStartCommand start(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition) {
        return new TaskContextStartCommand(
                runtimeRunId(request),
                text(request == null ? null : request.getSessionId()),
                text(request == null ? null : request.getProjectId()),
                text(definition == null ? null : definition.getAgentId()),
                text(request == null ? null : request.getQuery()),
                skills(definition));
    }

    public TaskContextProgressCommand progress(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            String taskState,
            List<OpsRuntimeEvent> events,
            String summary) {
        return new TaskContextProgressCommand(
                runtimeRunId(request),
                text(request == null ? null : request.getSessionId()),
                text(request == null ? null : request.getProjectId()),
                text(definition == null ? null : definition.getAgentId()),
                text(request == null ? null : request.getQuery()),
                skills(definition),
                text(taskState),
                facts(events),
                text(summary));
    }

    public TaskContextFinishCommand finish(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            String status,
            String output,
            List<OpsRuntimeEvent> events) {
        return new TaskContextFinishCommand(
                runtimeRunId(request),
                text(request == null ? null : request.getSessionId()),
                text(request == null ? null : request.getProjectId()),
                text(definition == null ? null : definition.getAgentId()),
                text(request == null ? null : request.getQuery()),
                skills(definition),
                text(status),
                text(output),
                facts(events));
    }

    public Map<String, Object> view(TaskContextSnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", snapshot.persistenceId());
        result.put("runId", snapshot.runId());
        result.put("sessionId", snapshot.sessionId());
        result.put("projectId", snapshot.projectId());
        result.put("agentId", snapshot.agentId());
        result.put("taskState", snapshot.state().name());
        result.put("context", content(snapshot.content()));
        result.put("summary", snapshot.summary());
        result.put("lastEventId", snapshot.lastEventId());
        result.put("version", snapshot.version());
        result.put("createTime", time(snapshot.createdAt()));
        result.put("updateTime", time(snapshot.updatedAt()));
        return result;
    }

    public Map<String, Object> auditContext(TaskContextContent content) {
        Map<String, Object> source = content(content);
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : List.of(
                "goal", "knownFacts", "ruledOut", "openQuestions", "completedActions",
                "pendingActions", "lastToolResultsSummary", "usedSkills")) {
            Object value = source.get(key);
            if (value instanceof List<?> list) {
                safe.put(key, list.stream().limit(5).toList());
            } else {
                safe.put(key, abbreviate(text(value), 300));
            }
        }
        return safe;
    }

    public Map<String, Object> content(TaskContextContent content) {
        TaskContextContent safe = content == null ? TaskContextContent.empty() : content;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goal", safe.goal());
        result.put("temporaryConstraints", safe.temporaryConstraints());
        result.put("knownFacts", safe.knownFacts());
        result.put("ruledOut", safe.ruledOut());
        result.put("openQuestions", safe.openQuestions());
        result.put("completedActions", safe.completedActions());
        result.put("pendingActions", safe.pendingActions());
        result.put("lastToolResultsSummary", safe.lastToolResultsSummary());
        result.put("usedSkills", safe.usedSkills());
        return result;
    }

    private List<TaskContextEventFact> facts(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        return events.stream()
                .filter(event -> event != null)
                .map(event -> new TaskContextEventFact(
                        event.getEventType(), event.getStatus(), event.getSummary(), event.getContent()))
                .toList();
    }

    private List<String> skills(OpsAgentDefinition definition) {
        if (definition == null || definition.getSkills() == null) return List.of();
        return definition.getSkills().stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String runtimeRunId(OpsAgentChatRequest request) {
        if (request == null) return "";
        if (!text(request.getRunId()).isBlank()) return text(request.getRunId());
        if (request.getMetadata() == null) return "";
        Object analysisRequest = request.getMetadata().get(
                WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST);
        if (analysisRequest instanceof OpsAgentRunRequestDTO dto
                && !text(dto.getRunId()).isBlank()) {
            return text(dto.getRunId());
        }
        return "";
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = text(value);
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : DB_TIME.format(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
