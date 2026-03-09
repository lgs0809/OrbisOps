package cn.lgs.orbisops.application.skill;

public enum SkillCandidateTournamentRolloutMode {
    LEGACY_PRIMARY,
    STRICT_SHADOW,
    STRICT_PRIMARY;

    public boolean strictEnabled() {
        return this != LEGACY_PRIMARY;
    }

    public boolean strictPrimary() {
        return this == STRICT_PRIMARY;
    }
}
