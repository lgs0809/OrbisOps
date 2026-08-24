package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import java.util.List;
import java.util.Map;

/** Content review cannot grant capabilities. Transport failures throw for durable job retry. */
public interface SkillContentReviewPort {
    List<String> review(SkillPatchCandidate candidate, Map<String, Object> acceptedInput);
}
