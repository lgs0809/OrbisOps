package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** External deterministic evaluator for Skill package candidates. */
public interface SkillPatchRegressionEvaluationPort {

    SkillPatchRegressionResult evaluate(Map<String, Object> candidate);
}
