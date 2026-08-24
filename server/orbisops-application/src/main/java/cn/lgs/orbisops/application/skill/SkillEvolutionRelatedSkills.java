package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy;
import java.util.List;
import java.util.Map;

/** Bounded, scoped package snapshots prepared before the author sees the proposal. */
public record SkillEvolutionRelatedSkills(String projectId,List<Map<String,Object>> skills) {
    public SkillEvolutionRelatedSkills {
        if(projectId==null || projectId.isBlank()) throw new IllegalArgumentException("SKILL_PROJECT_ID_REQUIRED");
        skills=SkillEvolutionRelatedSkillPolicy.references(skills,projectId);
    }
}
