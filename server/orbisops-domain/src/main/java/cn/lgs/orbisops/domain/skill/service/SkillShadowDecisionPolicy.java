package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillShadowDecision;

import java.util.List;

/** Rejects any unsafe or regressive Shadow comparison regardless of model score. */
public class SkillShadowDecisionPolicy {

    public SkillShadowDecision decide(
            boolean valid,
            boolean modelPassed,
            double score,
            boolean routingRegression,
            boolean evidenceRegression,
            boolean toolCallRegression,
            boolean unsafe,
            List<String> reasons,
            double minimumScore) {
        double threshold = Math.max(0D, Math.min(1D, minimumScore));
        boolean passed = valid
                && modelPassed
                && !routingRegression
                && !evidenceRegression
                && !toolCallRegression
                && !unsafe
                && score >= threshold;
        return new SkillShadowDecision(
                passed,
                score,
                reasons == null ? List.of() : reasons);
    }
}
