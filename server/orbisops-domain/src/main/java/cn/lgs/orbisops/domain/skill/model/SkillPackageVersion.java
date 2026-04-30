package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

public record SkillPackageVersion(long id,
                                  SkillPackageKey key,
                                  String skillHash,
                                  int baseVersion,
                                  String baseSkillHash,
                                  String sourceRunId,
                                  String sourceSessionId,
                                  String evolutionJobId,
                                  String publishMode,
                                  String content,
                                  String sourceType,
                                  String sourceTraceId,
                                  String changeSummary,
                                  String packageHash,
                                  String manifestJson,
                                  String artifactHashesJson,
                                  String entrypoint,
                                  int artifactCount,
                                  long packageSize,
                                  Instant createdAt) {

    public SkillPackageVersion {
        if (key == null) {
            throw new IllegalArgumentException("SKILL_PACKAGE_KEY_REQUIRED");
        }
        skillHash = text(skillHash);
        baseVersion = Math.max(0, baseVersion);
        baseSkillHash = text(baseSkillHash);
        sourceRunId = text(sourceRunId);
        sourceSessionId = text(sourceSessionId);
        evolutionJobId = text(evolutionJobId);
        publishMode = text(publishMode);
        content = content == null ? "" : content;
        sourceType = text(sourceType);
        sourceTraceId = text(sourceTraceId);
        changeSummary = text(changeSummary);
        packageHash = text(packageHash);
        manifestJson = text(manifestJson);
        artifactHashesJson = text(artifactHashesJson);
        entrypoint = text(entrypoint);
        if (artifactCount < 0 || packageSize < 0) {
            throw new IllegalArgumentException("SKILL_PACKAGE_SIZE_METADATA_INVALID");
        }
    }

    public boolean sameImmutableContent(SkillPackageVersion other) {
        return other != null
                && key.equals(other.key)
                && skillHash.equals(other.skillHash)
                && packageHash.equals(other.packageHash)
                && manifestJson.equals(other.manifestJson)
                && artifactHashesJson.equals(other.artifactHashesJson)
                && content.equals(other.content)
                && artifactCount == other.artifactCount
                && packageSize == other.packageSize;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

}
