package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCanaryCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillFrozenCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for typed Skill release and canary facts. */
public interface SkillReleasePort {
    default java.util.Map<String,Object> observationContract(cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate candidate) {
        return java.util.Map.of();
    }

    default cn.lgs.orbisops.domain.skill.model.SkillCanaryEvidence canaryEvidence(SkillReleaseSnapshot release) {
        return cn.lgs.orbisops.domain.skill.model.SkillCanaryEvidence.unknown();
    }

    Optional<SkillReleaseSnapshot> findByCandidate(String candidateId);

    void create(SkillReleaseSnapshot release);

    List<SkillCanaryCandidateSnapshot> findCanaryCandidates(
            String projectId,
            String agentId,
            int limit);

    Optional<SkillFrozenCandidateSnapshot> findFrozenCandidate(
            String candidateId,
            String projectId,
            String skillHash,
            String releaseId,
            String agentId);

    List<SkillReleaseSnapshot> listEvaluable(int limit);

    boolean claim(
            String releaseId,
            SkillReleaseStatus fromStatus,
            SkillReleaseStatus toStatus);

    boolean complete(
            String releaseId,
            SkillReleaseStatus expectedStatus,
            SkillReleaseStatus status,
            String reasonCode,
            int releasedVersion,
            String releasedSkillHash,
            String targetSkillId);

    void markReconciliationRequired(String releaseId, String reasonCode);
}
