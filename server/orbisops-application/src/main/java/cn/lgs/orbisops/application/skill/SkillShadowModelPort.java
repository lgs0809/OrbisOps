package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** Model boundary for structured Skill shadow comparison. */
public interface SkillShadowModelPort {

    boolean available();

    SkillShadowModelResult evaluate(
            String candidateId,
            Map<String, Object> candidate,
            SkillPatchRegressionResult deterministicResult);
}
