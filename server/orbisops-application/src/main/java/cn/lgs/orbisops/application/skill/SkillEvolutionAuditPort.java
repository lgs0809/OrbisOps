package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;

public interface SkillEvolutionAuditPort {

    void recordJobCreated(SkillEvolutionJobSnapshot job);

    void recordJobCompleted(
            SkillEvolutionJobSnapshot job,
            String decision,
            SkillEvolutionPatchSnapshot patch,
            SkillEvolutionJobStatus terminalStatus);

    void recordJobFailed(
            SkillEvolutionJobSnapshot job,
            SkillEvolutionRetryTransition transition,
            String error);
}
