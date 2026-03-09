package cn.lgs.orbisops.domain.skill.model;

/** Immutable derivation relation between Skill versions or identities. */
public enum SkillLineageRelation {
    CREATED_AS,
    PATCHED_FROM,
    MERGED_FROM,
    SPLIT_FROM,
    COMPRESSED_FROM,
    REVIVED_FROM,
    ROLLED_BACK_FROM
}
