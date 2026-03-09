package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;

import java.util.List;
import java.util.Map;

/** Optional model reranker over an already bounded lightweight Skill recall set. */
public interface SkillRerankPort {

    Map<String, Double> scores(String query, List<SkillRuntimeCandidate> candidates);
}
