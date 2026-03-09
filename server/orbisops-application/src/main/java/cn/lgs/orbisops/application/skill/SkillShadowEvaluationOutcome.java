package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillShadowDecision;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed application outcome for pre-release Skill shadow evaluation. */
public record SkillShadowEvaluationOutcome(
        String candidateId,
        String status,
        String reasonCode,
        SkillShadowDecision decision,
        Map<String, Object> deterministicEvaluation,
        int evalCaseCount
) {

    public SkillShadowEvaluationOutcome {
        candidateId = text(candidateId);
        status = required(status, "SKILL_SHADOW_STATUS_REQUIRED");
        reasonCode = text(reasonCode);
        if (decision == null) throw new IllegalArgumentException("SKILL_SHADOW_DECISION_REQUIRED");
        deterministicEvaluation = deterministicEvaluation == null || deterministicEvaluation.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(deterministicEvaluation));
        evalCaseCount = Math.max(0, evalCaseCount);
    }

    public boolean passed() {
        return decision.passed();
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", candidateId);
        result.put("status", status);
        result.put("passed", decision.passed());
        result.put("score", decision.score());
        result.put("reasons", decision.reasons());
        if (!reasonCode.isBlank()) result.put("reasonCode", reasonCode);
        if (!deterministicEvaluation.isEmpty()) {
            result.put("deterministicEvaluation", deterministicEvaluation);
        }
        result.put("evalCaseCount", evalCaseCount);
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
