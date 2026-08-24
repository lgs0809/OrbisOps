package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillPatchRiskLevel;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Application use case for idempotent typed Skill patch candidates. */
public class SkillPatchCandidateApplicationService {

    private final SkillPatchCandidatePort candidatePort;

    public SkillPatchCandidateApplicationService(
            SkillPatchCandidatePort candidatePort) {
        if (candidatePort == null) {
            throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_PORT_REQUIRED");
        }
        this.candidatePort = candidatePort;
    }

    public Map<String, Object> create(Map<String, Object> request) {
        return SkillPatchCandidateView.of(createCandidate(request));
    }

    public SkillPatchCandidate createCandidate(Map<String, Object> request) {
        return candidatePort.create(draft(request));
    }

    public Map<String,Object> createEvolution(Map<String,Object> request, SkillEvolutionJobSnapshot claim, String sourceHash) {
        if (claim==null || claim.sourceId().isBlank() || sourceHash==null || sourceHash.isBlank())
            throw new IllegalStateException("SKILL_EVOLUTION_ACCEPTED_CLAIM_REQUIRED");
        return SkillPatchCandidateView.of(candidatePort.createEvolution(draft(request),claim,sourceHash,text(request.get("authoringPlanId")),text(request.get("authoringPlanHash"))));
    }

    private SkillPatchCandidate draft(Map<String,Object> request) {
        Map<String, Object> candidate = new LinkedHashMap<>(
                request == null ? Map.of() : request);
        String projectId = required(candidate.get("projectId"), "projectId 不能为空");
        candidate.put("projectId", projectId);
        candidate.put("scope", fallback(candidate.get("scope"), "PROJECT"));
        candidate.put("patchType", fallback(candidate.get("patchType"), "NO_CHANGE"));
        candidate.put("riskLevel", fallback(candidate.get("riskLevel"), "LOW").toUpperCase());
        String candidateHash = CanonicalObjectHasher.sha256(candidate);
        String candidateId = "skill-candidate-" + UUID.randomUUID();
        SkillPatchCandidate authored = new SkillPatchCandidate(
                candidateId,
                candidateHash,
                text(candidate.get("sourceRunId")),
                text(candidate.get("sourceType")),
                projectId,
                text(candidate.get("agentId")),
                text(candidate.get("scope")),
                text(candidate.get("targetSkillId")),
                text(candidate.get("patchType")),
                SkillPatchRiskLevel.require(text(candidate.get("riskLevel"))),
                number(candidate.get("baseSkillVersion")),
                text(candidate.get("baseSkillHash")),
                text(candidate.get("contextBundleHash")),
                list(candidate.get("evidenceRefs")),
                list(candidate.get("changes")),
                list(candidate.get("artifacts")),
                list(candidate.get("evalCases")),
                SkillPatchCandidateStatus.CANDIDATE,
                "",
                null,
                null);
        return authored;
    }

    public Map<String, Object> get(String candidateId) {
        return SkillPatchCandidateView.of(getCandidate(candidateId));
    }

    public SkillPatchCandidate getCandidate(String candidateId) {
        return candidatePort.get(required(candidateId, "SKILL_PATCH_CANDIDATE_ID_REQUIRED"));
    }

    public void transition(
            String candidateId,
            String fromStatus,
            String toStatus,
            String reasonCode) {
        transition(
                candidateId,
                SkillPatchCandidateStatus.require(fromStatus),
                SkillPatchCandidateStatus.require(toStatus),
                reasonCode);
    }

    public void transition(
            String candidateId,
            SkillPatchCandidateStatus fromStatus,
            SkillPatchCandidateStatus toStatus,
            String reasonCode) {
        if (fromStatus == null || toStatus == null) {
            throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_STATUS_REQUIRED");
        }
        if (!candidatePort.transition(
                required(candidateId, "SKILL_PATCH_CANDIDATE_ID_REQUIRED"),
                fromStatus,
                toStatus,
                text(reasonCode))) {
            throw new IllegalStateException("SKILL_CANDIDATE_STATE_CONFLICT");
        }
    }

    public void updateStatus(
            String candidateId,
            String status,
            String reasonCode) {
        updateStatus(candidateId, SkillPatchCandidateStatus.require(status), reasonCode);
    }

    public void updateStatus(
            String candidateId,
            SkillPatchCandidateStatus status,
            String reasonCode) {
        if (status == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_STATUS_REQUIRED");
        candidatePort.updateStatus(
                required(candidateId, "SKILL_PATCH_CANDIDATE_ID_REQUIRED"),
                status,
                text(reasonCode));
    }

    private List<Object> list(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Object> result = new ArrayList<>();
        iterable.forEach(result::add);
        return List.copyOf(result);
    }

    private int number(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        try {
            return Math.max(0, Integer.parseInt(text(value)));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String fallback(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
