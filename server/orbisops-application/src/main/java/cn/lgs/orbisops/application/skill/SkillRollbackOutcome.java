package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;

public record SkillRollbackOutcome(SkillCatalogEntry previous,
                                   SkillPackageVersion publishedVersion) {

    public SkillRollbackOutcome {
        if (previous == null) throw new IllegalArgumentException("SKILL_ROLLBACK_PREVIOUS_REQUIRED");
        if (publishedVersion == null) throw new IllegalArgumentException("SKILL_ROLLBACK_VERSION_REQUIRED");
    }
}
