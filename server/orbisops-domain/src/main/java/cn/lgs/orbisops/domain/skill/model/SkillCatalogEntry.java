package cn.lgs.orbisops.domain.skill.model;

import java.time.LocalDateTime;

public record SkillCatalogEntry(long id,
                                String skillId,
                                String projectId,
                                String name,
                                String scope,
                                String sourceGlobalSkillId,
                                String description,
                                String content,
                                int version,
                                String status,
                                String createBy,
                                LocalDateTime createTime,
                                LocalDateTime updateTime,
                                String origin,
                                String updateMode,
                                boolean autoUpdateEnabled,
                                boolean autoMergeEnabled,
                                LocalDateTime lastEvolvedAt,
                                String frozenReason,
                                String frozenBy,
                                LocalDateTime frozenAt,
                                String skillHash,
                                int currentVersion,
                                String currentSkillHash,
                                int versionSeq,
                                String currentPackageHash,
                                String packageManifestJson,
                                String artifactHashesJson,
                                SkillGovernanceState governanceState) {

    public SkillCatalogEntry {
        skillId = safe(skillId);
        projectId = safe(projectId);
        name = safe(name);
        scope = safe(scope);
        sourceGlobalSkillId = safe(sourceGlobalSkillId);
        description = safe(description);
        content = content == null ? "" : content;
        status = safe(status);
        createBy = safe(createBy);
        origin = safe(origin);
        updateMode = safe(updateMode);
        frozenReason = safe(frozenReason);
        frozenBy = safe(frozenBy);
        skillHash = safe(skillHash);
        currentSkillHash = safe(currentSkillHash);
        currentPackageHash = safe(currentPackageHash);
        packageManifestJson = packageManifestJson == null ? "" : packageManifestJson;
        artifactHashesJson = artifactHashesJson == null ? "" : artifactHashesJson;
        governanceState = governanceState == null
                ? SkillGovernanceState.fromLegacy(status, updateMode, frozenReason, frozenBy, frozenAt)
                : governanceState;
        if (skillId.isBlank() || scope.isBlank() || version <= 0 || currentVersion <= 0 || versionSeq <= 0) {
            throw new IllegalArgumentException("SKILL_CATALOG_ENTRY_IDENTITY_REQUIRED");
        }
    }

    /** Compatibility constructor for pre-604 callers; dual-read derives fail-closed governance facts. */
    public SkillCatalogEntry(long id,
                             String skillId,
                             String projectId,
                             String name,
                             String scope,
                             String sourceGlobalSkillId,
                             String description,
                             String content,
                             int version,
                             String status,
                             String createBy,
                             LocalDateTime createTime,
                             LocalDateTime updateTime,
                             String origin,
                             String updateMode,
                             boolean autoUpdateEnabled,
                             boolean autoMergeEnabled,
                             LocalDateTime lastEvolvedAt,
                             String frozenReason,
                             String frozenBy,
                             LocalDateTime frozenAt,
                             String skillHash,
                             int currentVersion,
                             String currentSkillHash,
                             int versionSeq,
                             String currentPackageHash,
                             String packageManifestJson,
                             String artifactHashesJson) {
        this(id, skillId, projectId, name, scope, sourceGlobalSkillId, description, content,
                version, status, createBy, createTime, updateTime, origin, updateMode,
                autoUpdateEnabled, autoMergeEnabled, lastEvolvedAt, frozenReason, frozenBy,
                frozenAt, skillHash, currentVersion, currentSkillHash, versionSeq,
                currentPackageHash, packageManifestJson, artifactHashesJson, null);
    }

    public SkillPackageKey key() {
        return new SkillPackageKey(scope, projectId, skillId, currentVersion);
    }

    public String autoPublishSkipReason() {
        if (governanceState.legacyFrozenClassificationRequired()) {
            return "LEGACY_FROZEN_CLASSIFICATION_REQUIRED";
        }
        if (governanceState.mutationMode() == SkillMutationMode.LOCKED) return "LOCKED_SKIP";
        if (governanceState.mutationMode() == SkillMutationMode.SEALED) return "SEALED_SKIP";
        if (governanceState.mutationMode() == SkillMutationMode.MANUAL_ONLY || !autoUpdateEnabled) {
            return "MANUAL_ONLY_SKIP";
        }
        if (!governanceState.autoPublishAllowed()) return "STATUS_SKIP";
        return "";
    }

    public boolean legacyFrozenClassificationRequired() {
        return governanceState.legacyFrozenClassificationRequired();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
