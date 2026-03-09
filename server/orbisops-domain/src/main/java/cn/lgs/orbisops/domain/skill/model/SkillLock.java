package cn.lgs.orbisops.domain.skill.model;

import java.time.LocalDateTime;

public record SkillLock(SkillLockType type,
                        String reason,
                        String actor,
                        String approvalId,
                        LocalDateTime lockedAt) {

    public SkillLock {
        type = type == null ? SkillLockType.NONE : type;
        reason = text(reason);
        actor = text(actor);
        approvalId = text(approvalId);
        if (type == SkillLockType.NONE) {
            reason = "";
            actor = "";
            approvalId = "";
            lockedAt = null;
        } else {
            if (reason.isBlank()) throw new IllegalArgumentException("SKILL_LOCK_REASON_REQUIRED");
            if (actor.isBlank()) throw new IllegalArgumentException("SKILL_LOCK_ACTOR_REQUIRED");
            if (lockedAt == null) throw new IllegalArgumentException("SKILL_LOCK_TIME_REQUIRED");
        }
    }

    public static SkillLock none() {
        return new SkillLock(SkillLockType.NONE, "", "", "", null);
    }

    public boolean active() {
        return type != SkillLockType.NONE;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
