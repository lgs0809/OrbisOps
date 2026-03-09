package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillPatchRegressionEvaluationPort;
import cn.lgs.orbisops.application.skill.SkillPatchRegressionResult;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillEvalSuiteRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger adapter over the deterministic Skill package regression runner. */
@Component
public class OpsSkillPatchRegressionEvaluationAdapter
        implements SkillPatchRegressionEvaluationPort {

    private final OpsSkillEvalSuiteRunner evalSuiteRunner;

    public OpsSkillPatchRegressionEvaluationAdapter(
            OpsSkillEvalSuiteRunner evalSuiteRunner) {
        this.evalSuiteRunner = evalSuiteRunner;
    }

    @Override
    public SkillPatchRegressionResult evaluate(
            Map<String, Object> candidate) {
        OpsSkillEvalSuiteRunner.Result result = evalSuiteRunner.run(candidate);
        return new SkillPatchRegressionResult(
                result.passed(),
                result.failures(),
                result.caseResults(),
                result.baseline(),
                result.caseCount());
    }
}
