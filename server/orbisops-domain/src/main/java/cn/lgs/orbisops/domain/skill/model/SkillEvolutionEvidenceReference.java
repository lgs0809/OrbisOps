package cn.lgs.orbisops.domain.skill.model;

/** Stable evidence pointer extracted from a Skill Evolution trace payload. */
public record SkillEvolutionEvidenceReference(
        String evidenceId,
        String resultId,
        String outputHash) {

    public SkillEvolutionEvidenceReference {
        evidenceId = text(evidenceId);
        resultId = text(resultId);
        outputHash = text(outputHash);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
