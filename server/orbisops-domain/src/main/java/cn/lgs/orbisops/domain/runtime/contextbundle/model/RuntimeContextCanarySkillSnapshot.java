package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Published canary Skill identity frozen into one Runtime Context bundle.
 * Release-internal patch content is intentionally excluded from this boundary.
 */
public record RuntimeContextCanarySkillSnapshot(
        String skillId,
        int version,
        String skillHash,
        String candidateId,
        String releaseId,
        String projectId,
        String agentId
) {

    public RuntimeContextCanarySkillSnapshot {
        skillId = required(skillId, "RUNTIME_CANARY_SKILL_ID_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("RUNTIME_CANARY_SKILL_VERSION_REQUIRED");
        skillHash = required(skillHash, "RUNTIME_CANARY_SKILL_HASH_REQUIRED");
        candidateId = required(candidateId, "RUNTIME_CANARY_CANDIDATE_ID_REQUIRED");
        releaseId = required(releaseId, "RUNTIME_CANARY_RELEASE_ID_REQUIRED");
        projectId = required(projectId, "RUNTIME_CANARY_PROJECT_ID_REQUIRED");
        agentId = text(agentId);
    }

    public Map<String, Object> canonicalView() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillId", skillId);
        result.put("version", version);
        result.put("skillHash", skillHash);
        result.put("scope", "PROJECT");
        result.put("statusAtUse", "CANARY");
        result.put("selectedReason", "STABLE_HASH_CANARY");
        result.put("candidateId", candidateId);
        result.put("releaseId", releaseId);
        result.put("projectId", projectId);
        result.put("agentId", agentId);
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
