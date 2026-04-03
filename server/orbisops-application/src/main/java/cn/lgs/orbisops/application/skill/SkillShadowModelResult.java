package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

/** Structured model comparison for one Skill shadow evaluation. */
public record SkillShadowModelResult(
        boolean valid,
        boolean passed,
        double score,
        boolean routingRegression,
        boolean evidenceRegression,
        boolean toolCallRegression,
        boolean unsafe,
        List<String> reasons,
        Map<String, Object> raw) {

    public SkillShadowModelResult {
        score = Math.max(0D, Math.min(1D, score));
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }
}
