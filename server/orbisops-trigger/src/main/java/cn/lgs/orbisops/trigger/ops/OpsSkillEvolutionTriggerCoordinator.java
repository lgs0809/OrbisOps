package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.skill.SkillEvolutionJobApplicationService;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillEvolutionJobMapper;

import java.util.Map;

/** Trigger admission and enqueue projection for Skill Evolution jobs. */
final class OpsSkillEvolutionTriggerCoordinator {

    private final SkillEvolutionJobApplicationService applicationService;
    private final OpsSkillEvolutionJobMapper mapper;
    private final OpsSkillEvolutionSettings settings;

    OpsSkillEvolutionTriggerCoordinator(
            SkillEvolutionJobApplicationService applicationService,
            OpsSkillEvolutionJobMapper mapper,
            OpsSkillEvolutionSettings settings) {
        this.applicationService = applicationService;
        this.mapper = mapper;
        this.settings = settings;
    }

    Map<String, Object> enqueue(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            String triggerReason) {
        if (!settings.triggerEnabled()) {
            return Map.of("queued", false, "reason", "TRIGGER_DISABLED");
        }
        return mapper.enqueue(applicationService.enqueue(
                runId,
                sessionId,
                projectId,
                agentId,
                triggerReason));
    }
}
