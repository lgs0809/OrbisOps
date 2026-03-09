package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed authoritative Skill catalog facts plus a read-only compatibility projection. */
public record SkillCatalogSnapshot(
        SkillRuntimeCandidate runtimeCandidate,
        Map<String, Object> view
) {

    public SkillCatalogSnapshot {
        if (runtimeCandidate == null) {
            throw new IllegalArgumentException("SKILL_RUNTIME_CANDIDATE_REQUIRED");
        }
        view = view == null || view.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(view));
        if (!runtimeCandidate.skillId().equals(text(view.get("skillId")))) {
            throw new IllegalArgumentException("SKILL_CATALOG_VIEW_ID_MISMATCH");
        }
    }

    public static SkillCatalogSnapshot fromView(Map<String, Object> view) {
        return new SkillCatalogSnapshot(
                new SkillRuntimeCandidateAssembler().fromView(view),
                view);
    }

    public String skillId() {
        return runtimeCandidate.skillId();
    }

    public boolean activeAtUse() {
        return runtimeCandidate.activeAtUse();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
