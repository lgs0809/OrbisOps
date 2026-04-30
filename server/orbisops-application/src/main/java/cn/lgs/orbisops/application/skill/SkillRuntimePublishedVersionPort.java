package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import java.util.List;

/** Selects complete published versions within the independently computed current authorization. */
@FunctionalInterface
public interface SkillRuntimePublishedVersionPort {
    List<SkillRuntimeCandidate> visible(String projectId, List<SkillRuntimeCandidate> currentAuthorized);
    default List<SkillRuntimeCandidate> visible(String projectId,List<SkillRuntimeCandidate> current,java.util.Set<String> explicit) {
        return visible(projectId,current);
    }
    default List<SkillRuntimeCandidate> usableFrozen(String projectId,List<SkillRuntimeCandidate> frozen) {return frozen;}
}
