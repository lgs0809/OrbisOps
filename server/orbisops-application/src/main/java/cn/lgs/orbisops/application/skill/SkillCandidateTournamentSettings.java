package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

import java.util.Locale;

public record SkillCandidateTournamentSettings(
        SkillCandidateTournamentRolloutMode mode,
        String hiddenSuiteVersion,
        SkillVerifierVersion verifierVersion
) {

    public SkillCandidateTournamentSettings {
        mode = mode == null
                ? SkillCandidateTournamentRolloutMode.LEGACY_PRIMARY
                : mode;
        hiddenSuiteVersion = required(
                hiddenSuiteVersion, "SKILL_TOURNAMENT_HIDDEN_SUITE_VERSION_REQUIRED");
        if (verifierVersion == null) {
            throw new IllegalArgumentException("SKILL_VERIFIER_VERSION_REQUIRED");
        }
    }

    public static SkillCandidateTournamentSettings legacy() {
        return new SkillCandidateTournamentSettings(
                SkillCandidateTournamentRolloutMode.LEGACY_PRIMARY,
                "disabled",
                new SkillVerifierVersion("disabled", "disabled", "disabled"));
    }

    public static SkillCandidateTournamentSettings from(
            String mode,
            String hiddenSuiteVersion,
            String structuralVersion,
            String behaviorVersion,
            String judgeVersion) {
        SkillCandidateTournamentRolloutMode parsed;
        try {
            parsed = SkillCandidateTournamentRolloutMode.valueOf(
                    required(mode, "SKILL_TOURNAMENT_MODE_REQUIRED")
                            .toUpperCase(Locale.ROOT)
                            .replace('-', '_'));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_MODE_INVALID:" + mode, error);
        }
        return new SkillCandidateTournamentSettings(
                parsed,
                hiddenSuiteVersion,
                new SkillVerifierVersion(
                        structuralVersion,
                        behaviorVersion,
                        judgeVersion));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
