package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillBehaviorReplayResult(
        SkillBehaviorReplayArm arm,
        String activeSkillHash,
        SkillBehaviorMetrics metrics,
        String finalAnswerHash,
        String evidenceHash,
        List<String> toolResultIds,
        List<String> reasonCodes
) {

    public SkillBehaviorReplayResult {
        if (arm == null) throw new IllegalArgumentException("SKILL_REPLAY_ARM_REQUIRED");
        activeSkillHash = activeSkillHash == null ? "" : activeSkillHash.trim();
        if (arm != SkillBehaviorReplayArm.NO_SKILL && activeSkillHash.isBlank()) {
            throw new IllegalArgumentException("SKILL_REPLAY_ACTIVE_SKILL_HASH_REQUIRED");
        }
        if (metrics == null) throw new IllegalArgumentException("SKILL_REPLAY_METRICS_REQUIRED");
        finalAnswerHash = required(finalAnswerHash, "SKILL_REPLAY_ANSWER_HASH_REQUIRED");
        evidenceHash = required(evidenceHash, "SKILL_REPLAY_EVIDENCE_HASH_REQUIRED");
        toolResultIds = immutable(toolResultIds);
        reasonCodes = immutable(reasonCodes);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
