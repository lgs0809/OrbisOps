package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.OpsSkillEvolutionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsSkillEvolutionWorkerJobTest {

    @Test
    void scheduledAdapterDelegatesToCompatibilityEntry() {
        OpsSkillEvolutionService service = mock(OpsSkillEvolutionService.class);
        OpsSkillEvolutionWorkerJob job = new OpsSkillEvolutionWorkerJob(service);

        job.run();

        verify(service).scheduledRun();
    }
}
