package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public record SkillPackageKey(String scope, String projectId, String skillId, int version) {

    public SkillPackageKey {
        scope = requireText(scope, "scope").toUpperCase(Locale.ROOT);
        if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) {
            throw new IllegalArgumentException("SKILL_PACKAGE_SCOPE_INVALID：" + scope);
        }
        projectId = projectId == null ? "" : projectId.trim();
        if ("PROJECT".equals(scope) && projectId.isBlank()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_PROJECT_REQUIRED");
        }
        if ("GLOBAL".equals(scope)) {
            projectId = "";
        }
        skillId = requireText(skillId, "skillId");
        if (version <= 0) {
            throw new IllegalArgumentException("SKILL_PACKAGE_VERSION_INVALID：" + version);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_" + field.toUpperCase(Locale.ROOT) + "_REQUIRED");
        }
        return value.trim();
    }
}
