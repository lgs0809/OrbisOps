package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.skill.SkillEvolutionJobApplicationService;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillEvolutionJobMapper;

import java.util.List;
import java.util.Map;

/** Worker admission, bounded batch execution, and result projection. */
final class OpsSkillEvolutionWorkerCoordinator {

    private final SkillEvolutionJobApplicationService applicationService;
    private final OpsSkillEvolutionJobMapper mapper;
    private final OpsSkillEvolutionSettings settings;

    OpsSkillEvolutionWorkerCoordinator(
            SkillEvolutionJobApplicationService applicationService,
            OpsSkillEvolutionJobMapper mapper,
            OpsSkillEvolutionSettings settings) {
        this.applicationService = applicationService;
        this.mapper = mapper;
        this.settings = settings;
    }

    List<Map<String, Object>> runBatch() {
        if (!settings.workerEnabled()) {
            return List.of(Map.of("status", "SKIPPED", "reason", "WORKER_DISABLED"));
        }
        if (settings.triggerEnabled()) {
            applicationService.enqueueUnobservedCompletedRuns(
                    Math.max(settings.batchSize(), settings.batchSize() * 10));
        }
        return applicationService.runBatch(
                        settings.batchSize(),
                        settings.maxAttempts())
                .stream()
                .map(mapper::runResult)
                .toList();
    }
}
