package cn.lgs.orbisops.domain.skill.model;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable authoritative facts for one authored Skill patch candidate. */
public record SkillPatchCandidate(
        String candidateId,
        String candidateHash,
        String sourceRunId,
        String sourceType,
        String projectId,
        String agentId,
        String scope,
        String targetSkillId,
        String patchType,
        SkillPatchRiskLevel riskLevel,
        int baseSkillVersion,
        String baseSkillHash,
        String contextBundleHash,
        List<Object> evidenceRefs,
        List<Object> changes,
        List<Object> artifacts,
        List<Object> evalCases,
        SkillPatchCandidateStatus status,
        String reasonCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public SkillPatchCandidate {
        candidateId = required(candidateId, "SKILL_PATCH_CANDIDATE_ID_REQUIRED");
        candidateHash = required(candidateHash, "SKILL_PATCH_CANDIDATE_HASH_REQUIRED");
        sourceRunId = text(sourceRunId);
        sourceType = text(sourceType);
        projectId = required(projectId, "SKILL_PATCH_CANDIDATE_PROJECT_REQUIRED");
        agentId = text(agentId);
        scope = fallback(scope, "PROJECT");
        targetSkillId = text(targetSkillId);
        patchType = fallback(patchType, "NO_CHANGE");
        if (riskLevel == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_RISK_REQUIRED");
        baseSkillVersion = Math.max(0, baseSkillVersion);
        baseSkillHash = text(baseSkillHash);
        contextBundleHash = text(contextBundleHash);
        evidenceRefs = copy(evidenceRefs);
        changes = copy(changes);
        artifacts = copy(artifacts);
        evalCases = copy(evalCases);
        if (status == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_STATUS_REQUIRED");
        reasonCode = text(reasonCode);
    }

    public SkillPatchCandidate withStatus(
            SkillPatchCandidateStatus target,
            String reason) {
        if (target == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_STATUS_REQUIRED");
        return new SkillPatchCandidate(
                candidateId,
                candidateHash,
                sourceRunId,
                sourceType,
                projectId,
                agentId,
                scope,
                targetSkillId,
                patchType,
                riskLevel,
                baseSkillVersion,
                baseSkillHash,
                contextBundleHash,
                evidenceRefs,
                changes,
                artifacts,
                evalCases,
                target,
                reason,
                createdAt,
                updatedAt);
    }

    private static List<Object> copy(List<?> values) {
        return values == null || values.isEmpty()
                ? List.of()
                : List.copyOf(values);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
