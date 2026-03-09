package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillDefectDiagnosis;

import java.util.List;

/** Typed persistence boundary for Skill defect diagnoses. */
public interface SkillDefectDiagnosisPort {

    SkillDefectDiagnosis save(SkillDefectDiagnosis diagnosis);

    List<SkillDefectDiagnosis> findBySkill(
            String skillId,
            long skillVersion,
            int limit);
}
