package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;

/** Typed persistence boundary for structured Skill patch candidates. */
public interface SkillPatchCandidatePort {

    SkillPatchCandidate create(SkillPatchCandidate candidate);

    /** Autonomous proposals require a current accepted source and a live job attempt at the write boundary. */
    default SkillPatchCandidate createEvolution(SkillPatchCandidate candidate, SkillEvolutionJobSnapshot claim, String sourceHash, String planId, String planHash) {
        throw new IllegalStateException("SKILL_EVOLUTION_FENCED_CANDIDATE_STORE_REQUIRED");
    }

    SkillPatchCandidate get(String candidateId);

    boolean transition(
            String candidateId,
            SkillPatchCandidateStatus fromStatus,
            SkillPatchCandidateStatus toStatus,
            String reasonCode);

    void updateStatus(
            String candidateId,
            SkillPatchCandidateStatus status,
            String reasonCode);
}
