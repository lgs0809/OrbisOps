package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import java.util.List;

/** Durable active pointer; activation requires immutable body and route generation readiness. */
public interface SkillRoutePublicationPort {
    boolean activate(SkillRuntimeCandidate candidate, String modelIdentity);
    List<SkillRuntimeCandidate> visible(String projectId, List<SkillRuntimeCandidate> currentAuthorized, String modelIdentity);
    default List<SkillRuntimeCandidate> visible(String projectId,List<SkillRuntimeCandidate> current,String identity,java.util.Set<String> explicit) {
        return visible(projectId,current,identity);
    }
    default List<SkillRuntimeCandidate> lifecycleVisible(String projectId,List<SkillRuntimeCandidate> current,java.util.Set<String> explicit) {
        return current;
    }
}
