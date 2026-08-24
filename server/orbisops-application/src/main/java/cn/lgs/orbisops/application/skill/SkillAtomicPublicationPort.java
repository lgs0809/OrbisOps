package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillAtomicPublicationPlan;
import java.util.List;

/** Packages and this receipt share one transaction; runtime pointers switch only as a complete set. */
public interface SkillAtomicPublicationPort {
    void stage(String candidateId, SkillAtomicPublicationPlan plan, List<SkillPublicationOutcome> packages);
    boolean active(String candidateId);
    void rollback(String projectId, String candidateId, String actor, String reason);
    List<java.util.Map<String,Object>> list(String projectId,int limit);
}
