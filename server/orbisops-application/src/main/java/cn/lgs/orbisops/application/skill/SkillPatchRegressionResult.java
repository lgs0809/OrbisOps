package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

/** Deterministic regression-evaluation result for a Skill candidate. */
public record SkillPatchRegressionResult(
        boolean passed,
        List<String> failures,
        List<Map<String, Object>> cases,
        Map<String, Object> summary,
        int evaluatedCount) {

    public SkillPatchRegressionResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
        cases = cases == null ? List.of() : List.copyOf(cases);
        summary = summary == null ? Map.of() : Map.copyOf(summary);
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "passed", passed,
                "failures", failures,
                "cases", cases,
                "summary", summary,
                "evaluatedCount", evaluatedCount);
    }
}
