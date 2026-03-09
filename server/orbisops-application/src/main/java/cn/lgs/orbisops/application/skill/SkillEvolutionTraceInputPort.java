package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionTraceEvent;

import java.util.List;

public interface SkillEvolutionTraceInputPort {

    List<SkillEvolutionTraceEvent> trace(String runId);
}
