package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compatibility projection for typed Skill patch candidate facts. */
public final class SkillPatchCandidateView {

    private SkillPatchCandidateView() {
    }

    public static Map<String, Object> of(SkillPatchCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", candidate.candidateId());
        result.put("candidate_id", candidate.candidateId());
        result.put("candidateHash", candidate.candidateHash());
        result.put("candidate_hash", candidate.candidateHash());
        result.put("sourceRunId", candidate.sourceRunId());
        result.put("source_run_id", candidate.sourceRunId());
        result.put("sourceType", candidate.sourceType());
        result.put("source_type", candidate.sourceType());
        result.put("projectId", candidate.projectId());
        result.put("project_id", candidate.projectId());
        result.put("agentId", candidate.agentId());
        result.put("agent_id", candidate.agentId());
        result.put("scope", candidate.scope());
        result.put("targetSkillId", candidate.targetSkillId());
        result.put("target_skill_id", candidate.targetSkillId());
        result.put("patchType", candidate.patchType());
        result.put("patch_type", candidate.patchType());
        result.put("riskLevel", candidate.riskLevel().name());
        result.put("risk_level", candidate.riskLevel().name());
        result.put("baseSkillVersion", candidate.baseSkillVersion());
        result.put("base_skill_version", candidate.baseSkillVersion());
        result.put("baseSkillHash", candidate.baseSkillHash());
        result.put("base_skill_hash", candidate.baseSkillHash());
        result.put("contextBundleHash", candidate.contextBundleHash());
        result.put("context_bundle_hash", candidate.contextBundleHash());
        result.put("evidenceRefs", candidate.evidenceRefs());
        result.put("changes", candidate.changes());
        result.put("artifacts", candidate.artifacts());
        result.put("evalCases", candidate.evalCases());
        result.put("evidence_refs_json", CanonicalJson.stringify(candidate.evidenceRefs()));
        result.put("changes_json", CanonicalJson.stringify(candidate.changes()));
        result.put("artifacts_json", CanonicalJson.stringify(candidate.artifacts()));
        result.put("eval_cases_json", CanonicalJson.stringify(candidate.evalCases()));
        result.put("status", candidate.status().name());
        result.put("reasonCode", candidate.reasonCode());
        result.put("reason_code", candidate.reasonCode());
        result.put("createTime", time(candidate.createdAt()));
        result.put("updateTime", time(candidate.updatedAt()));
        return Map.copyOf(result);
    }

    private static String time(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
