package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCanaryApplicationService;
import org.springframework.stereotype.Service;

/** Trigger compatibility facade for stable Skill canary exposure. */
@Service
public class OpsSkillCanaryService {

    private final SkillCanaryApplicationService applicationService;

    public OpsSkillCanaryService(
            SkillCanaryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public boolean selected(
            String projectId,
            String agentId,
            String runId) {
        return applicationService.selected(projectId, agentId, runId);
    }

    public int percent() {
        return applicationService.percent();
    }
}
