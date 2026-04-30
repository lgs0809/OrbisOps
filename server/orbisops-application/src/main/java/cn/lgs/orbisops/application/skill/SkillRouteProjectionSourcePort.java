package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import java.util.List;

public interface SkillRouteProjectionSourcePort {
    List<SkillCatalogEntry> page(long afterId,int limit);
    boolean current(SkillRuntimeCandidate candidate);
}
