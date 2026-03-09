package cn.lgs.orbisops.application.skill;

/** Stable facts published by the external Skill similarity boundary. */
public record SkillEvolutionSimilarityMatch(
        String skillId,
        int version,
        String skillHash,
        double similarity,
        boolean frozen,
        String reason
) {

    public SkillEvolutionSimilarityMatch {
        skillId = text(skillId);
        if (version < 0) throw new IllegalArgumentException("SKILL_SIMILARITY_VERSION_INVALID");
        skillHash = text(skillHash);
        if (!Double.isFinite(similarity) || similarity < 0D || similarity > 1D) {
            throw new IllegalArgumentException("SKILL_SIMILARITY_SCORE_INVALID");
        }
        reason = text(reason);
    }

    public static SkillEvolutionSimilarityMatch none() {
        return new SkillEvolutionSimilarityMatch("", 0, "", 0D, false, "");
    }

    public boolean matched() {
        return !skillId.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
