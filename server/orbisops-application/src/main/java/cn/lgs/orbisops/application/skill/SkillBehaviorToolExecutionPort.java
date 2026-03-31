package cn.lgs.orbisops.application.skill;

@FunctionalInterface
public interface SkillBehaviorToolExecutionPort {
    SkillBehaviorToolResult execute(SkillBehaviorToolExecutionRequest request);
}
