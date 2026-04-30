package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;

public record SkillCatalogWriteOutcome(boolean created,
                                       SkillCatalogEntry previous,
                                       SkillPackageVersion publishedVersion) {

    public SkillCatalogWriteOutcome {
        if (publishedVersion == null) {
            throw new IllegalArgumentException("SKILL_CATALOG_WRITE_VERSION_REQUIRED");
        }
        if (created && previous != null) {
            throw new IllegalArgumentException("SKILL_CATALOG_CREATE_PREVIOUS_UNEXPECTED");
        }
        if (!created && previous == null) {
            throw new IllegalArgumentException("SKILL_CATALOG_UPDATE_PREVIOUS_REQUIRED");
        }
    }
}
