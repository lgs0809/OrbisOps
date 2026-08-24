package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCanaryCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillFrozenCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.service.SkillCanaryApplicabilityPolicy;
import java.util.Comparator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves and verifies frozen canary Skill references for one runtime bundle. */
public class SkillCanaryContextApplicationService {

    private final SkillReleasePort releasePort;
    private final SkillCanaryApplicationService canaryService;

    public SkillCanaryContextApplicationService(
            SkillReleasePort releasePort,
            SkillCanaryApplicationService canaryService) {
        if (releasePort == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_PORT_REQUIRED");
        }
        if (canaryService == null) {
            throw new IllegalArgumentException("SKILL_CANARY_SERVICE_REQUIRED");
        }
        this.releasePort = releasePort;
        this.canaryService = canaryService;
    }

    public List<SkillCanaryCandidateSnapshot> resolveCandidates(
            String projectId,
            String agentId,
            String runId) {
        // A legacy caller without task text cannot establish applicability.
        return resolveCandidates(projectId, agentId, runId, "");
    }

    public List<SkillCanaryCandidateSnapshot> resolveCandidates(
            String projectId, String agentId, String runId, String query) {
        if (query == null || query.isBlank() || !canaryService.selected(projectId, agentId, runId)) return List.of();
        var applicability = new SkillCanaryApplicabilityPolicy();
        return releasePort.findCanaryCandidates(projectId, agentId, 20).stream()
                .map(candidate -> Map.entry(candidate, applicability.score(projectId, agentId, query, candidate)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<SkillCanaryCandidateSnapshot, Double>, Double>comparing(Map.Entry::getValue)
                        .reversed().thenComparing(entry -> entry.getKey().releaseId()))
                .limit(3).map(Map.Entry::getKey).toList();
    }

    public List<Map<String, Object>> resolveRefs(
            String projectId,
            String agentId,
            String runId) {
        return resolveCandidates(projectId, agentId, runId).stream()
                .map(this::compatibilityRef)
                .toList();
    }

    public String context(
            String projectId,
            String agentId,
            String runId) {
        return renderFrozen(projectId, agentId, resolveRefs(projectId, agentId, runId));
    }

    public String renderFrozen(
            String projectId,
            String agentId,
            List<Map<String, Object>> frozenRefs) {
        if (frozenRefs == null || frozenRefs.isEmpty()) return "";
        String normalizedProjectId = text(projectId);
        String normalizedAgentId = text(agentId);
        if (normalizedProjectId.isBlank()) {
            throw new IllegalStateException("CANARY_SKILL_PROJECT_REQUIRED");
        }
        List<String> changes = new ArrayList<>();
        for (Map<String, Object> ref : frozenRefs) {
            String candidateId = text(ref.get("candidateId"));
            String releaseId = text(ref.get("releaseId"));
            String skillHash = text(ref.get("skillHash"));
            int version = number(ref.get("version"));
            if (candidateId.isBlank()
                    || releaseId.isBlank()
                    || skillHash.isBlank()
                    || version <= 0
                    || !normalizedProjectId.equals(text(ref.get("projectId")))) {
                throw new IllegalStateException("CANARY_SKILL_REF_INCOMPLETE");
            }
            SkillFrozenCandidateSnapshot candidate = releasePort.findFrozenCandidate(
                            candidateId,
                            normalizedProjectId,
                            skillHash,
                            releaseId,
                            normalizedAgentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "CANARY_SKILL_REF_STALE_OR_TAMPERED:" + candidateId));
            if (candidate.runtimeVersion() != version) {
                throw new IllegalStateException(
                        "CANARY_SKILL_VERSION_MISMATCH:" + candidateId);
            }
            changes.add(candidate.changesJson());
        }
        return "### Canary Skill 候选（仅影响审核前诊断方法，不是事实、权限或生产执行授权）\n"
                + String.join("\n", changes);
    }

    private Map<String, Object> compatibilityRef(SkillCanaryCandidateSnapshot candidate) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("skillId", candidate.runtimeSkillId());
        ref.put("version", candidate.runtimeVersion());
        ref.put("skillHash", candidate.candidateHash());
        ref.put("scope", "PROJECT");
        ref.put("statusAtUse", candidate.releaseStatus().name());
        ref.put("selectedReason", "STABLE_HASH_CANARY");
        ref.put("candidateId", candidate.candidateId());
        ref.put("releaseId", candidate.releaseId());
        ref.put("projectId", candidate.projectId());
        ref.put("agentId", candidate.agentId());
        return Map.copyOf(ref);
    }

    private int number(Object value) {
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
