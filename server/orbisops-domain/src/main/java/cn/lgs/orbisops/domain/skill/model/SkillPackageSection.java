package cn.lgs.orbisops.domain.skill.model;

public record SkillPackageSection(
        String sectionId,
        SkillPackageSectionType type,
        String content,
        int tokenEstimate,
        int priority,
        boolean required
) {

    public SkillPackageSection {
        sectionId = required(sectionId, "SKILL_PACKAGE_SECTION_ID_REQUIRED");
        if (type == null) throw new IllegalArgumentException("SKILL_PACKAGE_SECTION_TYPE_REQUIRED");
        content = required(content, "SKILL_PACKAGE_SECTION_CONTENT_REQUIRED");
        if (tokenEstimate <= 0) throw new IllegalArgumentException("SKILL_PACKAGE_SECTION_TOKEN_INVALID");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
