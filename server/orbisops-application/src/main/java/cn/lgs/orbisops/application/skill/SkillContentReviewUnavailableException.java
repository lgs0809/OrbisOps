package cn.lgs.orbisops.application.skill;

/** Deferred background work, never a reason to stop the user's conversation. */
public final class SkillContentReviewUnavailableException extends RuntimeException {
    public SkillContentReviewUnavailableException(Throwable cause) {
        super("SKILL_CONTENT_REVIEW_UNAVAILABLE", cause);
    }
}
