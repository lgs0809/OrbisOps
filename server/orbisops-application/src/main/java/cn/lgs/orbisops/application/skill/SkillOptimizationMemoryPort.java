package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemory;

import java.util.List;

/** Optimization-only memory boundary. It is not a conversation memory port. */
public interface SkillOptimizationMemoryPort {

    SkillOptimizationMemory save(SkillOptimizationMemory memory);

    List<SkillOptimizationMemory> findBySkill(
            String skillId,
            int limit);
}
