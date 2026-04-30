package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillConfusionEdge;

import java.util.List;

public interface SkillConfusionGraphPort {

    void replaceEdges(List<SkillConfusionEdge> edges);

    List<SkillConfusionEdge> neighbors(String skillId, int limit);
}
