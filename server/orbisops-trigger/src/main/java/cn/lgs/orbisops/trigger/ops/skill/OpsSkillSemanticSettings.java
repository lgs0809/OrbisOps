package cn.lgs.orbisops.trigger.ops.skill;

/** Typed semantic ranking bounds for Skill retrieval. */
public record OpsSkillSemanticSettings(
        boolean semanticEnabled,
        int candidateLimit,
        int cacheSize) {

    public OpsSkillSemanticSettings {
        candidateLimit = candidateLimit < 1 || candidateLimit > 128
                ? 64
                : candidateLimit;
        cacheSize = cacheSize < 128 || cacheSize > 65_536
                ? 2_048
                : cacheSize;
    }

    public static OpsSkillSemanticSettings defaults() {
        return new OpsSkillSemanticSettings(true, 64, 2_048);
    }
}
