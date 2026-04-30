package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCandidateTournamentResult;

public record SkillCandidateSetSelectionResult(
        SkillCandidateSelectionMode mode,
        SkillEvolutionAuthoredCandidate selectedCandidate,
        SkillCandidateTournamentResult tournamentResult,
        String reasonCode
) {

    public SkillCandidateSetSelectionResult {
        if (mode == null) throw new IllegalArgumentException("SKILL_SELECTION_MODE_REQUIRED");
        selectedCandidate = selectedCandidate == null
                ? SkillEvolutionAuthoredCandidate.from(java.util.Map.of())
                : selectedCandidate;
        reasonCode = required(reasonCode, "SKILL_SELECTION_REASON_REQUIRED");
        if (mode == SkillCandidateSelectionMode.STRICT_TOURNAMENT
                && tournamentResult == null) {
            throw new IllegalArgumentException("SKILL_STRICT_TOURNAMENT_RESULT_REQUIRED");
        }
        if (tournamentResult != null && tournamentResult.selected()
                && !selectedCandidate.reusableChange()) {
            throw new IllegalArgumentException("SKILL_SELECTED_CANDIDATE_NOT_REUSABLE");
        }
    }

    public boolean selected() {
        return selectedCandidate.reusableChange();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
