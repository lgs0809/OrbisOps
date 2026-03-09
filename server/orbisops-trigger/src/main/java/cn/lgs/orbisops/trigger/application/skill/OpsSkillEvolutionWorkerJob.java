package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.OpsSkillEvolutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduling adapter for the optional Skill Evolution worker. */
@Component
public class OpsSkillEvolutionWorkerJob {

    private final OpsSkillEvolutionService evolutionService;

    public OpsSkillEvolutionWorkerJob(OpsSkillEvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @Scheduled(fixedDelayString = "#{@opsSkillEvolutionSettings.fixedDelayMillis()}")
    public void run() {
        evolutionService.scheduledRun();
    }
}
