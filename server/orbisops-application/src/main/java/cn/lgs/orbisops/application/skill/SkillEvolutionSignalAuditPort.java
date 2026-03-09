package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;

import java.util.List;

/** Secondary port for Skill Evolution signal and hint audit projections. */
public interface SkillEvolutionSignalAuditPort {

    void recordSignalCreated(SkillEvolutionSignalSnapshot signal);

    void recordHintsConsumed(String candidateId, List<String> hintIds);
}
