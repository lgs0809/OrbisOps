package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillLockType {
    NONE,
    MANUAL_LOCK,
    STABILITY_LOCK,
    INCIDENT_QUARANTINE,
    COMPLIANCE_SEAL,
    LEGACY_UNCLASSIFIED;

    public static SkillLockType require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return NONE;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_LOCK_TYPE_UNKNOWN:" + normalized);
        }
    }
}
