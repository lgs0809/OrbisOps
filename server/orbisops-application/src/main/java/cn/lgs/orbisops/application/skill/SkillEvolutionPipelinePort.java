package cn.lgs.orbisops.application.skill;

public interface SkillEvolutionPipelinePort {

    SkillEvolutionPipelineDecision decide(SkillEvolutionPipelineRequest request);
}
