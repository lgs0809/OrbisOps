package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillCanaryCandidateSnapshot;
import java.util.Map;

/** Canary exposure obeys the same positive and negative routing boundary as stable Skills. */
public final class SkillCanaryApplicabilityPolicy {
    private final SkillRoutingProfilePolicy profiles = new SkillRoutingProfilePolicy();
    private final SkillRoutingBoundaryPolicy boundaries = new SkillRoutingBoundaryPolicy();

    public double score(String projectId, String agentId, String query, SkillCanaryCandidateSnapshot candidate) {
        if (candidate == null || query == null || query.isBlank() || !candidate.projectId().equals(projectId)
                || (!candidate.agentId().isBlank() && !candidate.agentId().equals(agentId))) return 0;
        try {
            var routing = CanonicalJson.parseArray(candidate.changesJson()).stream()
                    .filter(item -> item instanceof Map<?, ?> change && "routingProfile".equals(change.get("section")))
                    .map(item -> ((Map<?, ?>) item).get("value")).toList();
            if (routing.size() != 1 || !(routing.get(0) instanceof Map<?, ?> fields)) return 0;
            var profile = profiles.requireCanonicalProfile(text(fields.get("category")), text(fields.get("subcategory")),
                    "", "", fields.get("whenToUse"), fields.get("whenNotToUse"), fields.get("keywords"));
            if (!boundaries.validate(profile).isEmpty()) return 0;
            var match = boundaries.match(query, profile);
            return match.matched() ? match.positiveScore() : 0;
        } catch (IllegalArgumentException invalidProfile) {
            return 0;
        }
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
