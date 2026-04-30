package cn.lgs.orbisops.application.skill;

/** Catalog mutation result, distinct from merely closing a release record. */
public record SkillReleaseRecoveryOutcome(boolean restored, int version, String skillHash, String reason) { }
