package cn.lgs.orbisops.domain.skill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Neutral trace event used by Skill Evolution input analysis. */
public record SkillEvolutionTraceEvent(
        String eventType,
        String status,
        String summary,
        String content,
        Map<String, Object> payload) {

    /**
     * Backward-compatible constructor for trace producers that only expose a
     * summary. Final-output producers should use the content-aware constructor
     * so Skill Evolution can author from the actual user-visible conclusion.
     */
    public SkillEvolutionTraceEvent(
            String eventType,
            String status,
            String summary,
            Map<String, Object> payload) {
        this(eventType, status, summary, "", payload);
    }

    public SkillEvolutionTraceEvent {
        eventType = text(eventType);
        status = text(status);
        summary = text(summary);
        content = text(content);
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
