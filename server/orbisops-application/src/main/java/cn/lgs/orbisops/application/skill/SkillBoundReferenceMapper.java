package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps selected Skill versions into immutable runtime-bound references. */
public final class SkillBoundReferenceMapper {

    public Map<String, Object> runtimeVersionRef(
            SkillRuntimeSelection.RankedSkill ranked) {
        if (ranked == null || ranked.candidate() == null) {
            throw new IllegalArgumentException("SKILL_RUNTIME_RANKED_SKILL_REQUIRED");
        }
        SkillRuntimeCandidate skill = ranked.candidate();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("skillId", skill.skillId());
        ref.put("projectId", skill.projectId());
        ref.put("version", skill.version());
        ref.put("skillHash", skill.skillHash());
        ref.put("packageHash", skill.packageHash());
        ref.put("manifestHash", skill.manifestHash());
        ref.put("artifactHashes", skill.artifactHashes());
        ref.put("entrypoint", skill.entrypoint());
        ref.put("scope", skill.scope());
        ref.put("statusAtUse", skill.status());
        ref.put("status", skill.status());
        ref.put("updateMode", skill.updateMode());
        ref.put("category", skill.routingProfile().category());
        ref.put("subcategory", skill.routingProfile().subcategory());
        ref.put("selectedReason", ranked.explicit()
                ? "REQUESTED_ACTIVE_SKILL"
                : "QUERY_RELEVANCE");
        ref.put("relevanceScore", ranked.score());
        return Map.copyOf(ref);
    }

    public Map<String, Object> frozenCatalogRef(
            SkillRuntimeSelection.RankedSkill ranked) {
        Map<String, Object> ref = new LinkedHashMap<>(runtimeVersionRef(ranked));
        ref.put("name", ranked.candidate().name());
        ref.put("description", ranked.candidate().description());
        ref.put("contentLength", ranked.candidate().contentLength());
        ref.put("queryScore", ranked.score());
        return Map.copyOf(ref);
    }
}
