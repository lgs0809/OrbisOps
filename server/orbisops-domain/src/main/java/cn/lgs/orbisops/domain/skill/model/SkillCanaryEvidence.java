package cn.lgs.orbisops.domain.skill.model;

/** First assigned independent tasks, including unknowns; never a lifetime Run counter. */
public record SkillCanaryEvidence(int assignedTasks, int resolvedTasks, int succeededTasks,
        int unresolvedAssignments, int coveredConditions, int safetyViolations,
        int attributedRegressions, int safetyReviewedTasks) {
    public static SkillCanaryEvidence unknown() {
        return new SkillCanaryEvidence(0,0,0,1,0,0,0,0);
    }
    public String isolationReason() {
        if (safetyViolations > 0) return "CANARY_SAFETY_VIOLATION";
        if (attributedRegressions >= 2) return "CANARY_ATTRIBUTED_REGRESSION";
        return "";
    }
    public boolean promotable() {
        return assignedTasks == 20 && resolvedTasks == 20 && succeededTasks >= 19
                && unresolvedAssignments == 0 && coveredConditions >= 2 && safetyReviewedTasks == 20
                && isolationReason().isBlank();
    }
}
