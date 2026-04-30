package cn.lgs.orbisops.domain.skill.service;

import java.util.Set;

/** Definitive loss of a frozen baseline differs from a transport failure or an unknown write result. */
public final class SkillPublicationAdmissionPolicy {
    private static final Set<String> STALE=Set.of("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",
            "SKILL_EVOLUTION_TARGET_NOT_AUTOMATIC","SKILL_EVOLUTION_SOURCE_SET_INVALID","SKILL_EVOLUTION_SOURCE_REVOKED",
            "SKILL_ATOMIC_SOURCE_NOT_ACTIVE","SKILL_ATOMIC_SOURCE_ALREADY_REPLACED",
            "SKILL_ATOMIC_SOURCE_CHANGED_OR_PROTECTED","SKILL_ATOMIC_SOURCE_MERGE_DISABLED");
    public boolean stale(RuntimeException failure) {return failure.getMessage()!=null&&STALE.contains(failure.getMessage());}
    /** Legacy retries already diagnosed as stale need one current recheck, not transport backoff. */
    public java.util.List<String> retainedStaleReasons() {
        return STALE.stream().sorted().map(code->"RECONCILIATION_REQUIRED:"+code).toList();
    }
    public String reason(RuntimeException failure) {
        if(!stale(failure))throw new IllegalArgumentException("SKILL_PUBLICATION_STALE_FAILURE_REQUIRED");
        return "POLICY_PUBLICATION_BASELINE_STALE:"+failure.getMessage();
    }
}
