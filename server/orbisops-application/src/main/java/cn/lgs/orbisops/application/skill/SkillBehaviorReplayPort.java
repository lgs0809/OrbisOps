package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;

@FunctionalInterface
public interface SkillBehaviorReplayPort {
    SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArmRequest request,
            SkillBehaviorToolExecutionPort tools);
}
