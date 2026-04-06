package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPackageBudget;
import cn.lgs.orbisops.domain.skill.model.SkillPackageSection;
import cn.lgs.orbisops.domain.skill.model.SkillProgressiveLoadLevel;
import cn.lgs.orbisops.domain.skill.model.SkillProgressivePackage;
import cn.lgs.orbisops.domain.skill.service.SkillProgressivePackagePolicy;

import java.util.List;

public final class SkillProgressivePackageAssembler {

    private final SkillProgressivePackagePolicy policy;

    public SkillProgressivePackageAssembler() {
        this(new SkillProgressivePackagePolicy());
    }

    SkillProgressivePackageAssembler(SkillProgressivePackagePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("SKILL_PROGRESSIVE_POLICY_REQUIRED");
        this.policy = policy;
    }

    public SkillProgressivePackage assemble(
            String skillId,
            long skillVersion,
            SkillProgressiveLoadLevel level,
            List<SkillPackageSection> availableSections,
            SkillPackageBudget budget) {
        return policy.assemble(
                skillId, skillVersion, level, availableSections, budget);
    }
}
