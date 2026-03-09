package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillPatchValidationApplicationService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Trigger compatibility facade for Skill patch validation. */
@Service
public class OpsSkillPatchValidationService {

    private final SkillPatchValidationApplicationService applicationService;

    public OpsSkillPatchValidationService(
            SkillPatchValidationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Map<String, Object> validate(String candidateId) {
        return applicationService.validate(candidateId);
    }
}
