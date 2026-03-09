package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;
import java.util.List;

public record SkillOptimizationRound(
        int round,
        List<String> diagnosisIds,
        List<String> candidateIds,
        List<String> evaluationIds,
        String selectedCandidateId,
        String verifierVersion,
        String reasonCode,
        Instant startedAt,
        Instant finishedAt
) {

    public SkillOptimizationRound {
        if (round <= 0) throw new IllegalArgumentException("SKILL_OPTIMIZATION_ROUND_INVALID");
        diagnosisIds = immutable(diagnosisIds);
        candidateIds = immutable(candidateIds);
        evaluationIds = immutable(evaluationIds);
        selectedCandidateId = text(selectedCandidateId);
        verifierVersion = text(verifierVersion);
        reasonCode = text(reasonCode);
        if (startedAt == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_ROUND_START_REQUIRED");
    }

    public boolean completed() {
        return finishedAt != null;
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(SkillOptimizationRound::text).filter(value -> !value.isBlank())
                .distinct().sorted().toList();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
