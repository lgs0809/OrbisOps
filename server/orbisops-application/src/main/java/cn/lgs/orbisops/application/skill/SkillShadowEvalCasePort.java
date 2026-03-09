package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

/** Persistence boundary for historical Skill shadow evaluation cases. */
public interface SkillShadowEvalCasePort {

    void persist(
            String candidateId,
            Map<String, Object> candidate,
            List<?> cases,
            List<?> evidence,
            Map<String, Object> evaluation);
}
