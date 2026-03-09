package cn.lgs.orbisops.domain.skill.model;

/** Authoritative candidate facts used to verify one frozen canary reference. */
public record SkillFrozenCandidateSnapshot(
        String candidateId,
        String projectId,
        String agentId,
        String candidateHash,
        int baseSkillVersion,
        String changesJson
) {

    public SkillFrozenCandidateSnapshot {
        candidateId = required(candidateId, "SKILL_CANDIDATE_ID_REQUIRED");
        projectId = required(projectId, "SKILL_CANDIDATE_PROJECT_REQUIRED");
        agentId = text(agentId);
        candidateHash = required(candidateHash, "SKILL_CANDIDATE_HASH_REQUIRED");
        baseSkillVersion = Math.max(0, baseSkillVersion);
        changesJson = json(changesJson, "{}");
    }

    public int runtimeVersion() {
        return baseSkillVersion + 1;
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String json(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
