package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillLifecycleDecision;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleProposal;
import cn.lgs.orbisops.domain.skill.model.SkillLineageEdge;

import java.util.List;

public interface SkillLifecyclePort {

    void saveProposal(SkillLifecycleProposal proposal);

    void saveDecision(SkillLifecycleDecision decision);

    void saveLineage(List<SkillLineageEdge> edges);

    List<SkillLineageEdge> lineage(String skillId, int limit);
}
