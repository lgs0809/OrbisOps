package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Optional;

public interface SkillHiddenEvaluationSuitePort {

    SkillHiddenEvaluationSuiteSnapshot publish(SkillHiddenEvaluationSuiteDraft draft);

    Optional<SkillHiddenEvaluationSuiteSnapshot> find(
            String projectId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion);

    List<SkillHiddenEvaluationSuiteSnapshot> list(
            String projectId,
            String skillId,
            int limit);
}
