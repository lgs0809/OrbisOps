package cn.lgs.orbisops.domain.skill.model;

public record SkillLifecycleSubject(
        String skillId,
        long version,
        String skillHash,
        boolean sealed,
        SkillRetentionState retentionState
) {

    public SkillLifecycleSubject {
        skillId = required(skillId, "SKILL_LIFECYCLE_SKILL_ID_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("SKILL_LIFECYCLE_VERSION_INVALID");
        skillHash = required(skillHash, "SKILL_LIFECYCLE_HASH_REQUIRED");
        if (retentionState == null) throw new IllegalArgumentException("SKILL_RETENTION_STATE_REQUIRED");
    }

    public String reference() {
        return skillId + "@" + version;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
