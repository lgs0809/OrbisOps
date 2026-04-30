package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillDefectLayer;

import java.util.List;

public record SkillDefectDiagnosisCommand(
        String diagnosisId,
        String skillId,
        long skillVersion,
        SkillDefectLayer layer,
        String symptom,
        String rootCause,
        List<String> supportingTrajectoryIds,
        List<String> counterexampleIds,
        double confidence,
        String suggestedDirection
) {
}
