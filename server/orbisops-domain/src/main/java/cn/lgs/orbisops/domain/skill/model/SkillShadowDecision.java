package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Domain decision for pre-release Skill shadow comparison. */
public record SkillShadowDecision(
        boolean passed,
        double score,
        List<String> reasons) {

    public SkillShadowDecision {
        score = Math.max(0D, Math.min(1D, score));
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
