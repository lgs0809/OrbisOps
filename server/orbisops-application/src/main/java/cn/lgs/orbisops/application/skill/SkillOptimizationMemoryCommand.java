package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemoryType;

import java.util.List;

public record SkillOptimizationMemoryCommand(
        String memoryId,
        String skillId,
        long skillVersion,
        SkillOptimizationMemoryType type,
        String summary,
        List<String> evidenceIds,
        String modelCompatibility,
        String environmentCompatibility,
        boolean effective
) {
}
