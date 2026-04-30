package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

public record SkillConfusionEdge(
        String expectedSkillId,
        String selectedSkillId,
        int falsePositiveCount,
        int falseNegativeCount,
        int shadowingCount,
        double averageMargin,
        Instant evaluatedAt
) {

    public SkillConfusionEdge {
        expectedSkillId = required(expectedSkillId, "SKILL_CONFUSION_EXPECTED_SKILL_REQUIRED");
        selectedSkillId = required(selectedSkillId, "SKILL_CONFUSION_SELECTED_SKILL_REQUIRED");
        if (expectedSkillId.equals(selectedSkillId)) {
            throw new IllegalArgumentException("SKILL_CONFUSION_SELF_EDGE_FORBIDDEN");
        }
        if (falsePositiveCount < 0 || falseNegativeCount < 0 || shadowingCount < 0) {
            throw new IllegalArgumentException("SKILL_CONFUSION_COUNT_INVALID");
        }
        if (falsePositiveCount + falseNegativeCount + shadowingCount <= 0) {
            throw new IllegalArgumentException("SKILL_CONFUSION_EVIDENCE_REQUIRED");
        }
        if (!Double.isFinite(averageMargin) || averageMargin < 0D || averageMargin > 1D) {
            throw new IllegalArgumentException("SKILL_CONFUSION_MARGIN_INVALID");
        }
        if (evaluatedAt == null) throw new IllegalArgumentException("SKILL_CONFUSION_TIME_REQUIRED");
    }

    public String edgeKey() {
        return expectedSkillId + "->" + selectedSkillId;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
