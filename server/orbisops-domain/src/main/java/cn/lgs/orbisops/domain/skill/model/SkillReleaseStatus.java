package cn.lgs.orbisops.domain.skill.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Authoritative lifecycle state for a governed Skill release. */
public enum SkillReleaseStatus {
    READY,
    PENDING_INDEX,
    CANARY,
    PROMOTING,
    ACTIVE,
    ROLLING_BACK,
    ROLLED_BACK,
    DISABLED,
    PROMOTION_UNKNOWN,
    ROLLBACK_UNKNOWN;

    public static SkillReleaseStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return SkillReleaseStatus.valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("SKILL_RELEASE_STATUS_INVALID:" + normalized, error);
        }
    }

    public boolean canTransitionTo(SkillReleaseStatus target) {
        if (target == null) return false;
        return allowedTargets().contains(target);
    }

    public void requireTransitionTo(SkillReleaseStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "SKILL_RELEASE_TRANSITION_INVALID:" + name() + "->"
                            + (target == null ? "" : target.name()));
        }
    }

    public SkillReleaseStatus reconciliationRequiredStatus() {
        return switch (this) {
            case PROMOTING -> PROMOTION_UNKNOWN;
            case ROLLING_BACK -> ROLLBACK_UNKNOWN;
            default -> this;
        };
    }

    public boolean evaluable() {
        return this == READY || this == PENDING_INDEX || this == CANARY || this == ACTIVE;
    }

    private Set<SkillReleaseStatus> allowedTargets() {
        return switch (this) {
            case READY -> EnumSet.of(PROMOTING, ROLLED_BACK);
            case PENDING_INDEX -> EnumSet.of(ACTIVE, ROLLED_BACK);
            case CANARY -> EnumSet.of(PROMOTING, ROLLED_BACK);
            case PROMOTING -> EnumSet.of(PENDING_INDEX, ACTIVE, ROLLED_BACK, PROMOTION_UNKNOWN);
            case ACTIVE -> EnumSet.of(ROLLING_BACK);
            case ROLLING_BACK -> EnumSet.of(ROLLED_BACK, DISABLED, ROLLBACK_UNKNOWN);
            case ROLLED_BACK, DISABLED, PROMOTION_UNKNOWN, ROLLBACK_UNKNOWN -> EnumSet.noneOf(SkillReleaseStatus.class);
        };
    }
}
