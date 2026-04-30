package cn.lgs.orbisops.domain.skill.model;

public enum SkillOptimizationStatus {
    PLANNED,
    RUNNING,
    EVALUATING,
    SUCCEEDED,
    FAILED,
    ROUND_LIMIT_REACHED,
    CANCELED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == ROUND_LIMIT_REACHED || this == CANCELED;
    }
}
