package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionJobRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import java.time.*;
import java.util.concurrent.ScheduledFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpsSkillEvolutionLeaseConfigurationTest {
    @Test void slowWorkerRenewsIndependentlyAndClosesItsScheduledRegistration() {
        var repository=mock(ISkillEvolutionJobRepository.class);
        var scheduler=mock(TaskScheduler.class);
        var claim=mock(SkillEvolutionJobSnapshot.class);
        when(repository.renewLease(claim)).thenReturn(true,true,false);
        when(scheduler.getClock()).thenReturn(Clock.systemUTC());
        var future=mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class),any(Instant.class),any(Duration.class));
        var callback=ArgumentCaptor.forClass(Runnable.class);
        var scope=new OpsSkillEvolutionLeaseConfiguration().skillEvolutionLeasePort(repository,scheduler).maintain(claim);
        verify(scheduler).scheduleWithFixedDelay(callback.capture(),any(Instant.class),eq(Duration.ofSeconds(30)));
        scope.requireOwned();callback.getValue().run();scope.requireOwned();
        callback.getValue().run();assertThrows(IllegalStateException.class,scope::requireOwned);
        callback.getValue().run();verify(repository,times(3)).renewLease(claim);
        scope.close();verify(future).cancel(false);
    }
    @Test void expiredClaimNeverStartsModelWorkOrSchedulesRenewal() {
        var repository=mock(ISkillEvolutionJobRepository.class);var scheduler=mock(TaskScheduler.class);
        var port=new OpsSkillEvolutionLeaseConfiguration().skillEvolutionLeasePort(repository,scheduler);
        assertThrows(IllegalStateException.class,()->port.maintain(mock(SkillEvolutionJobSnapshot.class)));
        verifyNoInteractions(scheduler);
    }
}
