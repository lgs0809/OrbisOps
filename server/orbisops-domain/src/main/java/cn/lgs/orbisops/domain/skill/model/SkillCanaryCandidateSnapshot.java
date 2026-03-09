package cn.lgs.orbisops.domain.skill.model;

/** Frozen canary candidate facts joined from release and patch-candidate stores. */
public record SkillCanaryCandidateSnapshot(
        String releaseId,
        String candidateId,
        String projectId,
        String agentId,
        String targetSkillId,
        SkillReleaseStatus releaseStatus,
        String candidateHash,
        int baseSkillVersion,
        String baseSkillHash,
        String patchType,
        String changesJson,
        String artifactsJson
) {

    public SkillCanaryCandidateSnapshot {
        releaseId = required(releaseId, "SKILL_RELEASE_ID_REQUIRED");
        candidateId = required(candidateId, "SKILL_RELEASE_CANDIDATE_REQUIRED");
        projectId = required(projectId, "SKILL_RELEASE_PROJECT_REQUIRED");
        agentId = text(agentId);
        targetSkillId = text(targetSkillId);
        if (releaseStatus != SkillReleaseStatus.CANARY) {
            throw new IllegalArgumentException("SKILL_CANARY_RELEASE_STATUS_REQUIRED");
        }
        candidateHash = required(candidateHash, "SKILL_CANDIDATE_HASH_REQUIRED");
        baseSkillVersion = Math.max(0, baseSkillVersion);
        baseSkillHash = text(baseSkillHash);
        patchType = text(patchType);
        changesJson = json(changesJson, "{}");
        artifactsJson = json(artifactsJson, "[]");
    }

    public String runtimeSkillId() {
        return targetSkillId.isBlank() ? "candidate:" + candidateId : targetSkillId;
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
