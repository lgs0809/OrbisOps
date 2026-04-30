package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionChatInputPort;
import cn.lgs.orbisops.application.skill.SkillEvolutionTraceInputPort;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionMessage;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionTraceEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps Runtime trace and chat views into neutral Skill Evolution input models. */
@Component
public class OpsSkillEvolutionInputAdapter implements SkillEvolutionTraceInputPort, SkillEvolutionChatInputPort {

    private final GraphEventApplicationService graphEventService;
    private final OpsChatSessionService chatSessionService;

    public OpsSkillEvolutionInputAdapter(
            GraphEventApplicationService graphEventService,
            OpsChatSessionService chatSessionService) {
        this.graphEventService = graphEventService;
        this.chatSessionService = chatSessionService;
    }

    @Override
    public List<SkillEvolutionTraceEvent> trace(String runId) {
        String id = value(runId);
        if (id.isBlank()) return List.of();
        return graphEventService.list(id).stream()
                .filter(event -> event != null)
                .map(event -> new SkillEvolutionTraceEvent(
                        event.eventType(),
                        event.status(),
                        event.summary(),
                        contentOf(event.payload()),
                        event.payload()))
                .toList();
    }

    @Override
    public List<SkillEvolutionMessage> messages(String sessionId, int limit) {
        String id = value(sessionId);
        if (id.isBlank()) return List.of();
        return chatSessionService.messages(id, Math.max(1, limit)).stream()
                .filter(message -> message != null)
                .map(message -> new SkillEvolutionMessage(message.getRole(), message.getContent()))
                .toList();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String contentOf(java.util.Map<String, Object> payload) {
        if (payload == null) return "";
        Object content = payload.get("content");
        return content == null ? "" : String.valueOf(content);
    }
}
