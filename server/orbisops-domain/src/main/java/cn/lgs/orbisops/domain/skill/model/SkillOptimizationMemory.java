package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;
import java.util.List;

public record SkillOptimizationMemory(
        String memoryId,
        String skillId,
        long skillVersion,
        SkillOptimizationMemoryType type,
        String summary,
        List<String> evidenceIds,
        String modelCompatibility,
        String environmentCompatibility,
        boolean effective,
        Instant recordedAt
) {

    public SkillOptimizationMemory {
        memoryId = required(memoryId, "SKILL_OPTIMIZATION_MEMORY_ID_REQUIRED");
        skillId = required(skillId, "SKILL_OPTIMIZATION_MEMORY_SKILL_ID_REQUIRED");
        if (skillVersion <= 0) throw new IllegalArgumentException("SKILL_OPTIMIZATION_MEMORY_VERSION_INVALID");
        if (type == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_MEMORY_TYPE_REQUIRED");
        summary = required(summary, "SKILL_OPTIMIZATION_MEMORY_SUMMARY_REQUIRED");
        evidenceIds = evidenceIds == null ? List.of() : evidenceIds.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        modelCompatibility = text(modelCompatibility);
        environmentCompatibility = text(environmentCompatibility);
        if (recordedAt == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_MEMORY_TIME_REQUIRED");
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
