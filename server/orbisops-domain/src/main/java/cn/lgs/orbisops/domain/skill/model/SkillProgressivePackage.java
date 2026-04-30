package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillProgressivePackage(
        String skillId,
        long skillVersion,
        SkillProgressiveLoadLevel loadLevel,
        List<SkillPackageSection> sections,
        int totalTokens,
        boolean budgetExhausted,
        String packageHash
) {

    public SkillProgressivePackage {
        skillId = required(skillId, "SKILL_PROGRESSIVE_PACKAGE_SKILL_ID_REQUIRED");
        if (skillVersion <= 0) throw new IllegalArgumentException("SKILL_PROGRESSIVE_PACKAGE_VERSION_INVALID");
        if (loadLevel == null) throw new IllegalArgumentException("SKILL_PROGRESSIVE_LOAD_LEVEL_REQUIRED");
        sections = sections == null ? List.of() : List.copyOf(sections);
        if (sections.isEmpty()) throw new IllegalArgumentException("SKILL_PROGRESSIVE_PACKAGE_SECTIONS_REQUIRED");
        if (totalTokens <= 0) throw new IllegalArgumentException("SKILL_PROGRESSIVE_PACKAGE_TOKENS_INVALID");
        packageHash = required(packageHash, "SKILL_PROGRESSIVE_PACKAGE_HASH_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
