package cn.lgs.orbisops.application.skill;

public record SkillTournamentCandidate(
        String candidateId,
        String candidateHash,
        SkillEvolutionAuthoredCandidate authoredCandidate,
        int patchComplexity
) {

    public SkillTournamentCandidate {
        candidateId = required(candidateId, "SKILL_TOURNAMENT_CANDIDATE_ID_REQUIRED");
        candidateHash = required(candidateHash, "SKILL_TOURNAMENT_CANDIDATE_HASH_REQUIRED");
        if (authoredCandidate == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_AUTHORED_CANDIDATE_REQUIRED");
        }
        if (patchComplexity < 0) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_COMPLEXITY_INVALID");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
