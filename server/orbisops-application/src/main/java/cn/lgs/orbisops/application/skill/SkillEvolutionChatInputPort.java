package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionMessage;

import java.util.List;

public interface SkillEvolutionChatInputPort {

    List<SkillEvolutionMessage> messages(String sessionId, int limit);
}
