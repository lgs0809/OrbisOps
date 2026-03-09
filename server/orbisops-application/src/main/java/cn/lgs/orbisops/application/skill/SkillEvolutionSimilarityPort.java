package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** Outbound boundary for matching authored candidates against the Skill catalog. */
public interface SkillEvolutionSimilarityPort {

    SkillEvolutionSimilarityMatch bestMatch(String projectId, Map<String, Object> candidate);
}
