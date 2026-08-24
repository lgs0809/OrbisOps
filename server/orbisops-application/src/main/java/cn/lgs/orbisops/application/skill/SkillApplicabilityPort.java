package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillApplicabilityDecision;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import java.util.List;
import java.util.Map;

/** One bounded descriptive assessment of the already-authorized, versioned retrieval window. */
@FunctionalInterface
public interface SkillApplicabilityPort {
    Map<String, SkillApplicabilityDecision> assess(String projectId, String query, List<SkillRuntimeCandidate> candidates);
    SkillApplicabilityPort UNAVAILABLE = (project, query, candidates) -> Map.of();
}
