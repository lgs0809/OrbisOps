package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillRoutingEvaluationCaseResult(
        String caseId,
        SkillRoutingEvaluationCaseType caseType,
        String expectedSkillId,
        List<String> rankedSkillIds,
        double top1Score,
        double top2Score,
        boolean noSkillSelected
) {

    public SkillRoutingEvaluationCaseResult {
        caseId = required(caseId, "SKILL_ROUTING_CASE_ID_REQUIRED");
        if (caseType == null) throw new IllegalArgumentException("SKILL_ROUTING_CASE_TYPE_REQUIRED");
        expectedSkillId = text(expectedSkillId);
        rankedSkillIds = rankedSkillIds == null ? List.of() : rankedSkillIds.stream()
                .map(SkillRoutingEvaluationCaseResult::text)
                .filter(value -> !value.isBlank()).distinct().toList();
        top1Score = score(top1Score, "SKILL_ROUTING_TOP1_SCORE_INVALID");
        top2Score = score(top2Score, "SKILL_ROUTING_TOP2_SCORE_INVALID");
        if (top2Score > top1Score) {
            throw new IllegalArgumentException("SKILL_ROUTING_SCORE_ORDER_INVALID");
        }
        boolean positive = caseType == SkillRoutingEvaluationCaseType.POSITIVE
                || caseType == SkillRoutingEvaluationCaseType.HARD_POSITIVE
                || caseType == SkillRoutingEvaluationCaseType.CONFUSING_NEIGHBOR;
        if (positive && expectedSkillId.isBlank()) {
            throw new IllegalArgumentException("SKILL_ROUTING_EXPECTED_SKILL_REQUIRED");
        }
        if (noSkillSelected && !rankedSkillIds.isEmpty()) {
            throw new IllegalArgumentException("SKILL_ROUTING_NO_SKILL_WITH_RANKING");
        }
    }

    public double margin() {
        return top1Score - top2Score;
    }

    public String selectedSkillId() {
        return rankedSkillIds.isEmpty() ? "" : rankedSkillIds.get(0);
    }

    public boolean top1Correct() {
        return !expectedSkillId.isBlank() && expectedSkillId.equals(selectedSkillId());
    }

    public boolean top3Correct() {
        return !expectedSkillId.isBlank()
                && rankedSkillIds.stream().limit(3).anyMatch(expectedSkillId::equals);
    }

    private static double score(double value, String reasonCode) {
        if (!Double.isFinite(value) || value < 0D || value > 1D) {
            throw new IllegalArgumentException(reasonCode);
        }
        return value;
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
