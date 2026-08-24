package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import java.util.List;
import java.util.Map;

/** Rebuildable projection; the caller supplies current authorized exact version references. */
public interface SkillRouteIndexPort {
    Map<String,Double> search(String projectId,List<SkillRuntimeCandidate> authorized,String modelIdentity,float[] query,int limit);

    void stage(SkillRuntimeCandidate candidate,String modelIdentity);
    void ready(SkillRuntimeCandidate candidate,String modelIdentity,float[] embedding);
    boolean contains(SkillRuntimeCandidate candidate,String modelIdentity);
}
