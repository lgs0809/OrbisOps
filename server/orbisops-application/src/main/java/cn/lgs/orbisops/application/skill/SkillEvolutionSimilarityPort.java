package cn.lgs.orbisops.application.skill;

import java.util.Map;
import java.util.List;

/** Outbound boundary for matching authored candidates against the Skill catalog. */
public interface SkillEvolutionSimilarityPort {

    SkillEvolutionSimilarityMatch bestMatch(String projectId, Map<String, Object> candidate);

    default SkillEvolutionRelatedSkills relatedSkills(String projectId, Map<String, Object> input) {
        throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_STORE_REQUIRED");
    }

    default void validateRelatedSkills(String projectId,List<Map<String,Object>> relatedSkills) {
        throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_STORE_REQUIRED");
    }

    default SkillEvolutionSimilarityMatch bestFrozenMatch(String projectId, Map<String, Object> candidate,
                                                           List<Map<String, Object>> relatedSkills) {
        throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_STORE_REQUIRED");
    }
}
