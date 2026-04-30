package cn.lgs.orbisops.application.skill;

import java.time.Instant;

@FunctionalInterface
public interface SkillOptimizationClockPort {
    Instant now();
}
