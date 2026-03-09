package cn.lgs.orbisops.domain.skill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable persisted facts for one governed Skill release. */
public record SkillReleaseSnapshot(
        String releaseId,
        String candidateId,
        String projectId,
        String agentId,
        String targetSkillId,
        SkillReleaseStatus status,
        int canaryPercent,
        int baselineVersion,
        String baselineSkillHash,
        String reasonCode,
        int releasedVersion,
        String releasedSkillHash,
        Map<String, Object> metadata
) {

    public SkillReleaseSnapshot {
        releaseId = required(releaseId, "SKILL_RELEASE_ID_REQUIRED");
        candidateId = required(candidateId, "SKILL_RELEASE_CANDIDATE_REQUIRED");
        projectId = required(projectId, "SKILL_RELEASE_PROJECT_REQUIRED");
        agentId = text(agentId);
        targetSkillId = text(targetSkillId);
        if (status == null) throw new IllegalArgumentException("SKILL_RELEASE_STATUS_REQUIRED");
        canaryPercent = Math.max(0, Math.min(100, canaryPercent));
        baselineVersion = Math.max(0, baselineVersion);
        baselineSkillHash = text(baselineSkillHash);
        reasonCode = text(reasonCode);
        releasedVersion = Math.max(0, releasedVersion);
        releasedSkillHash = text(releasedSkillHash);
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String metricSkillId() {
        return targetSkillId.isBlank() ? "candidate:" + candidateId : targetSkillId;
    }

    public String effectiveTargetSkillId(String publishedSkillId) {
        String normalized = text(publishedSkillId);
        return normalized.isBlank() ? targetSkillId : normalized;
    }

    public SkillReleaseSnapshot transitionTo(SkillReleaseStatus target) {
        status.requireTransitionTo(target);
        return new SkillReleaseSnapshot(
                releaseId,
                candidateId,
                projectId,
                agentId,
                targetSkillId,
                target,
                canaryPercent,
                baselineVersion,
                baselineSkillHash,
                reasonCode,
                releasedVersion,
                releasedSkillHash,
                metadata);
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
