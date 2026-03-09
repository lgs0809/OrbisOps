package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillShadowApplicationService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Trigger compatibility facade for Skill shadow evaluation. */
@Service
public class OpsSkillShadowService {

    private final SkillShadowApplicationService applicationService;

    public OpsSkillShadowService(
            SkillShadowApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Map<String, Object> evaluate(String candidateId) {
        return applicationService.evaluate(candidateId);
    }
}
