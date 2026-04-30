package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;

public interface SkillOptimizationRunPort {

    SkillOptimizationRun save(SkillOptimizationRun run);

    SkillOptimizationRun get(String runId);
}
