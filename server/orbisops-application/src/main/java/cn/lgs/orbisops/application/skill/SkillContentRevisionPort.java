package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import java.util.List;
import java.util.Map;

/** Bounded semantic correction; provider outages do not consume a completed revision. */
public interface SkillContentRevisionPort {
    boolean revise(SkillPatchCandidate candidate,Map<String,Object> input,List<String> failures,Runnable leaseGuard);
    SkillContentRevisionPort NONE=(candidate,input,failures,guard)->false;
}
