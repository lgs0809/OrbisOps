package cn.lgs.orbisops.application.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SkillBehaviorToolResult(
        String resultId,
        boolean allowed,
        String outputHash,
        Map<String, Object> payload
) {

    public SkillBehaviorToolResult {
        resultId = required(resultId, "SKILL_REPLAY_TOOL_RESULT_ID_REQUIRED");
        outputHash = required(outputHash, "SKILL_REPLAY_TOOL_OUTPUT_HASH_REQUIRED");
        payload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
