package cn.lgs.orbisops.domain.skill.model;

import java.time.LocalDateTime;
import java.util.Set;

/** Complete next state for a mutable Skill catalog pointer guarded by version/hash CAS. */
public record SkillCatalogMutation(String scope,
                                   String projectId,
                                   String skillId,
                                   int baseVersion,
                                   String baseSkillHash,
                                   int nextVersion,
                                   String nextSkillHash,
                                   String name,
                                   String sourceGlobalSkillId,
                                   String description,
                                   String content,
                                   String status,
                                   String origin,
                                   String updateMode,
                                   boolean autoUpdateEnabled,
                                   boolean autoMergeEnabled,
                                   LocalDateTime lastEvolvedAt,
                                   String frozenReason,
                                   String frozenBy,
                                   LocalDateTime frozenAt,
                                   String packageHash,
                                   String manifestJson,
                                   String artifactHashesJson,
                                   SkillGovernanceState governanceState) {

    public SkillCatalogMutation {
        scope = required(scope, "SKILL_MUTATION_SCOPE_REQUIRED").toUpperCase();
        if (!Set.of("GLOBAL", "PROJECT").contains(scope)) {
            throw new IllegalArgumentException("SKILL_MUTATION_SCOPE_INVALID:" + scope);
        }
        projectId = text(projectId);
        if ("PROJECT".equals(scope) && projectId.isBlank()) {
            throw new IllegalArgumentException("SKILL_MUTATION_PROJECT_REQUIRED");
        }
        if ("GLOBAL".equals(scope)) projectId = "";
        skillId = required(skillId, "SKILL_MUTATION_ID_REQUIRED");
        if (baseVersion <= 0 || nextVersion != baseVersion + 1) {
            throw new IllegalArgumentException("SKILL_MUTATION_VERSION_INVALID");
        }
        baseSkillHash = text(baseSkillHash);
        nextSkillHash = required(nextSkillHash, "SKILL_MUTATION_NEXT_HASH_REQUIRED");
        name = required(name, "SKILL_MUTATION_NAME_REQUIRED");
        sourceGlobalSkillId = text(sourceGlobalSkillId);
        description = text(description);
        content = requiredRaw(content, "SKILL_MUTATION_CONTENT_REQUIRED");
        status = required(status, "SKILL_MUTATION_STATUS_REQUIRED").toUpperCase();
        origin = required(origin, "SKILL_MUTATION_ORIGIN_REQUIRED").toUpperCase();
        updateMode = required(updateMode, "SKILL_MUTATION_UPDATE_MODE_REQUIRED").toUpperCase();
        frozenReason = text(frozenReason);
        frozenBy = text(frozenBy);
        packageHash = required(packageHash, "SKILL_MUTATION_PACKAGE_HASH_REQUIRED");
        manifestJson = requiredRaw(manifestJson, "SKILL_MUTATION_MANIFEST_REQUIRED");
        artifactHashesJson = artifactHashesJson == null ? "" : artifactHashesJson;
        governanceState = governanceState == null
                ? SkillGovernanceState.fromLegacy(status, updateMode, frozenReason, frozenBy, frozenAt)
                : governanceState;
    }

    public SkillCatalogMutation(String scope,
                                String projectId,
                                String skillId,
                                int baseVersion,
                                String baseSkillHash,
                                int nextVersion,
                                String nextSkillHash,
                                String name,
                                String sourceGlobalSkillId,
                                String description,
                                String content,
                                String status,
                                String origin,
                                String updateMode,
                                boolean autoUpdateEnabled,
                                boolean autoMergeEnabled,
                                LocalDateTime lastEvolvedAt,
                                String frozenReason,
                                String frozenBy,
                                LocalDateTime frozenAt,
                                String packageHash,
                                String manifestJson,
                                String artifactHashesJson) {
        this(scope, projectId, skillId, baseVersion, baseSkillHash, nextVersion, nextSkillHash,
                name, sourceGlobalSkillId, description, content, status, origin, updateMode,
                autoUpdateEnabled, autoMergeEnabled, lastEvolvedAt, frozenReason, frozenBy,
                frozenAt, packageHash, manifestJson, artifactHashesJson, null);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String requiredRaw(String value, String reasonCode) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(reasonCode);
        return value;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
