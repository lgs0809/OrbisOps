package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;

/** Typed persistence boundary for structured Skill patch candidates. */
public interface SkillPatchCandidatePort {

    SkillPatchCandidate create(SkillPatchCandidate candidate);

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
