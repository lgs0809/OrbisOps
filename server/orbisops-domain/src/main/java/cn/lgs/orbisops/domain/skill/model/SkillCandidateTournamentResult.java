package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillCandidateTournamentResult(
        String tournamentId,
        String selectedCandidateId,
        List<SkillCandidateVerification> rankings,
        boolean manualReviewRequired,
        List<String> reasonCodes,
        SkillVerifierVersion verifierVersion
) {

    public SkillCandidateTournamentResult {
        tournamentId = required(tournamentId, "SKILL_TOURNAMENT_ID_REQUIRED");
        selectedCandidateId = selectedCandidateId == null ? "" : selectedCandidateId.trim();
        rankings = rankings == null ? List.of() : List.copyOf(rankings);
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        if (verifierVersion == null) throw new IllegalArgumentException("SKILL_VERIFIER_VERSION_REQUIRED");
        String effectiveSelectedCandidateId = selectedCandidateId;
        if (!effectiveSelectedCandidateId.isBlank()
                && rankings.stream().noneMatch(item -> item.candidateId().equals(effectiveSelectedCandidateId))) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_SELECTED_CANDIDATE_UNKNOWN");
        }
    }

    public boolean selected() {
        return !selectedCandidateId.isBlank();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
