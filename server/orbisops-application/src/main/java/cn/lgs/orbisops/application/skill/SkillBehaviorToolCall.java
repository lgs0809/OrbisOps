package cn.lgs.orbisops.application.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SkillBehaviorToolCall(
        String toolsetId,
        String toolName,
        Map<String, Object> arguments,
        boolean readOnly,
        boolean productionWrite,
        boolean changePackageProposal,
        boolean directLanding,
        String frozenResultId
) {

    public SkillBehaviorToolCall {
        toolsetId = required(toolsetId, "SKILL_REPLAY_TOOLSET_REQUIRED");
        toolName = required(toolName, "SKILL_REPLAY_TOOL_REQUIRED");
        arguments = arguments == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        frozenResultId = text(frozenResultId);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
