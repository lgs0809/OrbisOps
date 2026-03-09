package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** Persistence boundary for named Skill candidate validation results. */
public interface SkillPatchValidationResultPort {

    void upsert(
            String candidateId,
            String validationType,
            String status,
            double score,
            Map<String, Object> result);
}
