package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;

/** Stable publication facts consumed by the Skill Release state machine. */
public record SkillPublicationOutcome(
        boolean published,
        String reasonCode,
        String skillId,
        int version,
        String skillHash
) {

    public SkillPublicationOutcome {
        reasonCode = required(reasonCode, "SKILL_PUBLICATION_REASON_REQUIRED");
        skillId = text(skillId);
        skillHash = text(skillHash);
        if (published) {
            if (skillId.isBlank()) throw new IllegalArgumentException("SKILL_PUBLICATION_SKILL_ID_REQUIRED");
            if (version <= 0) throw new IllegalArgumentException("SKILL_PUBLICATION_VERSION_REQUIRED");
            if (skillHash.isBlank()) throw new IllegalArgumentException("SKILL_PUBLICATION_HASH_REQUIRED");
        } else if (version < 0) {
            throw new IllegalArgumentException("SKILL_PUBLICATION_VERSION_INVALID");
        }
    }

    public static SkillPublicationOutcome published(String reasonCode, SkillPackageVersion version) {
        if (version == null) throw new IllegalArgumentException("SKILL_PUBLICATION_VERSION_REQUIRED");
        return new SkillPublicationOutcome(
                true,
                reasonCode,
                version.key().skillId(),
                version.key().version(),
                version.skillHash());
    }

    public static SkillPublicationOutcome rejected(String reasonCode, SkillCatalogEntry current) {
        if (current == null) throw new IllegalArgumentException("SKILL_PUBLICATION_CURRENT_REQUIRED");
        String currentHash = current.currentSkillHash().isBlank()
                ? current.skillHash()
                : current.currentSkillHash();
        return new SkillPublicationOutcome(
                false,
                reasonCode,
                current.skillId(),
                current.currentVersion(),
                currentHash);
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
