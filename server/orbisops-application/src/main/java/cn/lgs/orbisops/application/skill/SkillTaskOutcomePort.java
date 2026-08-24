package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.VerifiedTaskOutcome;

public interface SkillTaskOutcomePort {
    VerifiedTaskOutcome verifiedSuccess(String projectId, String runId);
}
