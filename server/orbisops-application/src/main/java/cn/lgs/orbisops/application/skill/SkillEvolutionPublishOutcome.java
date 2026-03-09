package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;

public record SkillEvolutionPublishOutcome(boolean published,
                                           String reasonCode,
                                           SkillCatalogEntry current,
                                           SkillPackageVersion publishedVersion) {

    public SkillEvolutionPublishOutcome {
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        if (reasonCode.isBlank()) throw new IllegalArgumentException("SKILL_EVOLUTION_REASON_REQUIRED");
        if (current == null) throw new IllegalArgumentException("SKILL_EVOLUTION_CURRENT_REQUIRED");
        if (published && publishedVersion == null) {
            throw new IllegalArgumentException("SKILL_EVOLUTION_VERSION_REQUIRED");
        }
        if (!published && publishedVersion != null) {
            throw new IllegalArgumentException("SKILL_EVOLUTION_VERSION_UNEXPECTED");
        }
    }
}
