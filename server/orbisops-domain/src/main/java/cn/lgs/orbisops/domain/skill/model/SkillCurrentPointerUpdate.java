package cn.lgs.orbisops.domain.skill.model;

import java.util.Set;

/** CAS command for advancing a Skill catalog current pointer to a new immutable package version. */
public record SkillCurrentPointerUpdate(String scope,
                                        String projectId,
                                        String skillId,
                                        int baseVersion,
                                        String baseSkillHash,
                                        int nextVersion,
                                        String nextSkillHash,
                                        String name,
                                        String description,
                                        String content,
                                        String origin,
                                        String packageHash,
                                        String manifestJson,
                                        String artifactHashesJson) {

    public SkillCurrentPointerUpdate {
        scope = safe(scope).toUpperCase();
        projectId = safe(projectId);
        skillId = safe(skillId);
        baseSkillHash = safe(baseSkillHash);
        nextSkillHash = safe(nextSkillHash);
        name = safe(name);
        description = safe(description);
        content = content == null ? "" : content;
        origin = safe(origin).toUpperCase();
        packageHash = safe(packageHash);
        manifestJson = manifestJson == null ? "" : manifestJson;
        artifactHashesJson = artifactHashesJson == null ? "" : artifactHashesJson;
        if (!Set.of("GLOBAL", "PROJECT").contains(scope)
                || ("PROJECT".equals(scope) && projectId.isBlank())
                || skillId.isBlank()
                || baseVersion <= 0
                || nextVersion != baseVersion + 1
                || nextSkillHash.isBlank()
                || name.isBlank()
                || content.isBlank()
                || origin.isBlank()
                || packageHash.isBlank()
                || manifestJson.isBlank()) {
            throw new IllegalArgumentException("SKILL_CURRENT_POINTER_UPDATE_REQUIRED");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
