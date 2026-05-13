package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeGlobalAuthorizationCommand;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OpsKnowledgeAuthorizationCommandMapper {

    public KnowledgeGlobalAuthorizationCommand enableGlobal(String projectId,
                                                             Map<String, Object> request,
                                                             String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String globalKbId = firstText(
                safe.get("globalKbId"), safe.get("kbId"), safe.get("knowledgeTag"));
        KnowledgeStatus status = KnowledgeStatus.require(text(safe.getOrDefault("status", "ENABLED")));
        return new KnowledgeGlobalAuthorizationCommand(projectId, globalKbId, status, actor);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
