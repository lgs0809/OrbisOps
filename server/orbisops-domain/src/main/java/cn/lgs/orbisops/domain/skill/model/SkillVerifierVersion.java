package cn.lgs.orbisops.domain.skill.model;

public record SkillVerifierVersion(
        String structuralVersion,
        String behaviorVersion,
        String judgeVersion
) {

    public SkillVerifierVersion {
        structuralVersion = required(structuralVersion, "SKILL_STRUCTURAL_VERIFIER_VERSION_REQUIRED");
        behaviorVersion = required(behaviorVersion, "SKILL_BEHAVIOR_VERIFIER_VERSION_REQUIRED");
        judgeVersion = required(judgeVersion, "SKILL_JUDGE_VERSION_REQUIRED");
    }

    public String compositeId() {
        return structuralVersion + ":" + behaviorVersion + ":" + judgeVersion;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
