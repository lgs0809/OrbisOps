package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.memory.MemoryPostProcessingApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryFacadeSettings;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class OpsMemoryPostProcessingJobTest {
    @Test void disabledMemoryNeverDispatchesRetainedModelWork() {
        var service=mock(MemoryPostProcessingApplicationService.class);var settings=mock(OpsMemoryFacadeSettings.class);
        when(settings.enabled()).thenReturn(false);
        new OpsMemoryPostProcessingJob(service,settings).replay();verifyNoInteractions(service);
    }
}
