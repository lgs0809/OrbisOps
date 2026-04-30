package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

/** Authoritative lifecycle status for one Skill patch candidate. */
public enum SkillPatchCandidateStatus {
    CANDIDATE,
    PENDING_INDEX,
    PASSED,
    POLICY_REJECTED,
    VALIDATION_FAILED,
    SHADOW,
    CANARY,
    ACTIVE,
    ROLLED_BACK,
    REJECTED,
    DISABLED,
    PROMOTION_UNKNOWN,
    ROLLBACK_UNKNOWN;

    public static SkillPatchCandidateStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "SKILL_PATCH_CANDIDATE_STATUS_INVALID:" + normalized,
                    error);
        }
    }
}
