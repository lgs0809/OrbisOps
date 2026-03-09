package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillStructuralVerification(
        String candidateId,
        boolean passed,
        double score,
        List<String> reasonCodes,
        String verifierVersion
) {

    public SkillStructuralVerification {
        candidateId = required(candidateId, "SKILL_STRUCTURAL_CANDIDATE_ID_REQUIRED");
        if (!Double.isFinite(score) || score < 0D || score > 1D) {
            throw new IllegalArgumentException("SKILL_STRUCTURAL_SCORE_INVALID");
        }
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        verifierVersion = required(verifierVersion, "SKILL_STRUCTURAL_VERIFIER_VERSION_REQUIRED");
        if (passed && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("SKILL_STRUCTURAL_PASS_WITH_REASONS");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
