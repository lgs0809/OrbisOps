package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillRuntimeSelection(List<RankedSkill> catalog,
                                    List<RankedSkill> selected,
                                    List<SuppressedSkill> suppressed,
                                    int activeCount) {

    public SkillRuntimeSelection {
        catalog = catalog == null ? List.of() : List.copyOf(catalog);
        selected = selected == null ? List.of() : List.copyOf(selected);
        suppressed = suppressed == null ? List.of() : List.copyOf(suppressed);
    }

    public record RankedSkill(SkillRuntimeCandidate candidate, double score, boolean explicit) {
    }

    public record SuppressedSkill(RankedSkill rankedSkill, String suppressedBySkillId, String reasonCode) {
    }
}
