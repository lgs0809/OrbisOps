package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;

import java.util.List;
import java.util.Map;

/** Optional semantic scoring adapter for the bounded runtime Skill candidate set. */
public interface SkillSemanticScorePort {

    Map<String, Double> scores(String query, List<SkillRuntimeCandidate> candidates);
    default Map<String, Double> scores(String projectId,String query,List<SkillRuntimeCandidate> candidates) {
        return scores(query,candidates);
    }
}
