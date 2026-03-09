package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

/** Immutable target and verifier facts for one strict candidate tournament. */
public record SkillCandidateTournamentContext(
        String tournamentId,
        String projectId,
        String skillId,
        long baseVersion,
        String baseSkillHash,
        String hiddenSuiteVersion,
        SkillVerifierVersion verifierVersion
) {

    public SkillCandidateTournamentContext {
        tournamentId = required(tournamentId, "SKILL_TOURNAMENT_ID_REQUIRED");
        projectId = required(projectId, "SKILL_TOURNAMENT_PROJECT_ID_REQUIRED");
        skillId = required(skillId, "SKILL_TOURNAMENT_SKILL_ID_REQUIRED");
        if (baseVersion <= 0) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_BASE_VERSION_INVALID");
        }
        baseSkillHash = hash(baseSkillHash, "SKILL_TOURNAMENT_BASE_HASH_INVALID");
        hiddenSuiteVersion = required(
                hiddenSuiteVersion, "SKILL_TOURNAMENT_HIDDEN_SUITE_VERSION_REQUIRED");
        if (verifierVersion == null) {
            throw new IllegalArgumentException("SKILL_VERIFIER_VERSION_REQUIRED");
        }
    }

    private static String hash(String value, String reasonCode) {
        String normalized = required(value, reasonCode).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(reasonCode);
        }
        return normalized;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
