package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

/** Risk classification of an authored Skill patch candidate. */
public enum SkillPatchRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static SkillPatchRiskLevel require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("SKILL_PATCH_RISK_INVALID:" + normalized, error);
        }
    }
}
