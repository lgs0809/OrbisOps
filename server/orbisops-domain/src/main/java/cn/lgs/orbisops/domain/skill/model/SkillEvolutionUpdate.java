package cn.lgs.orbisops.domain.skill.model;

public record SkillEvolutionUpdate(String projectId,
                                   String skillId,
                                   int baseVersion,
                                   String baseSkillHash,
                                   int nextVersion,
                                   String nextSkillHash,
                                   String name,
                                   String description,
                                   String content,
                                   String packageHash,
                                   String manifestJson,
                                   String artifactHashesJson) {

    public SkillEvolutionUpdate {
        projectId = safe(projectId);
        skillId = safe(skillId);
        baseSkillHash = safe(baseSkillHash);
        nextSkillHash = safe(nextSkillHash);
        name = safe(name);
        description = safe(description);
        content = content == null ? "" : content;
        packageHash = safe(packageHash);
        manifestJson = manifestJson == null ? "" : manifestJson;
        artifactHashesJson = artifactHashesJson == null ? "" : artifactHashesJson;
        if (projectId.isBlank() || skillId.isBlank() || baseVersion <= 0 || baseSkillHash.isBlank()
                || nextVersion != baseVersion + 1 || nextSkillHash.isBlank() || packageHash.isBlank()) {
            throw new IllegalArgumentException("SKILL_EVOLUTION_UPDATE_IDENTITY_REQUIRED");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
