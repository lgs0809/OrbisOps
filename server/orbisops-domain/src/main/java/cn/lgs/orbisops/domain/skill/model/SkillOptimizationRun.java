package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;
import java.util.List;

public record SkillOptimizationRun(
        String runId,
        String skillId,
        long baseVersion,
        String baseSkillHash,
        SkillOptimizationStatus status,
        int maxRounds,
        List<SkillOptimizationRound> rounds,
        Instant createdAt,
        Instant updatedAt
) {

    public SkillOptimizationRun {
        runId = required(runId, "SKILL_OPTIMIZATION_RUN_ID_REQUIRED");
        skillId = required(skillId, "SKILL_OPTIMIZATION_SKILL_ID_REQUIRED");
        if (baseVersion <= 0) throw new IllegalArgumentException("SKILL_OPTIMIZATION_BASE_VERSION_INVALID");
        baseSkillHash = required(baseSkillHash, "SKILL_OPTIMIZATION_BASE_HASH_REQUIRED");
        if (status == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_STATUS_REQUIRED");
        if (maxRounds < 1 || maxRounds > 3) throw new IllegalArgumentException("SKILL_OPTIMIZATION_MAX_ROUNDS_INVALID");
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
        if (rounds.size() > maxRounds) throw new IllegalArgumentException("SKILL_OPTIMIZATION_ROUND_LIMIT_EXCEEDED");
        if (createdAt == null || updatedAt == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_TIME_REQUIRED");
    }

    public int nextRoundNumber() {
        return rounds.size() + 1;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
