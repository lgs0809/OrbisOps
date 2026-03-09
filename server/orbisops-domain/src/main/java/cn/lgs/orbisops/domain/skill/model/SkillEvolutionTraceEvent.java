package cn.lgs.orbisops.domain.skill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Neutral trace event used by Skill Evolution input analysis. */
public record SkillEvolutionTraceEvent(
        String eventType,
        String status,
        String summary,
        Map<String, Object> payload) {

    public SkillEvolutionTraceEvent {
        eventType = text(eventType);
        status = text(status);
        summary = text(summary);
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
