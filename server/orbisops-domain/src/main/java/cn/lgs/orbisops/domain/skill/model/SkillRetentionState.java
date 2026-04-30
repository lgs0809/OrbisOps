package cn.lgs.orbisops.domain.skill.model;

public enum SkillRetentionState {
    ACTIVE,
    DEPRECATED,
    HIDDEN_FROM_ROUTING,
    RETAINED_FOR_ROLLBACK,
    PURGE_ELIGIBLE
}
