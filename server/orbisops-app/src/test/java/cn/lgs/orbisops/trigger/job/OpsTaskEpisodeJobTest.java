package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.episode.TaskEpisodeApplicationService;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsTaskEpisodeJobTest {
    @Test void slowBackgroundReplayDoesNotBlockTheCallerOrQueueDuplicateWorkers() throws Exception {
        var service=mock(TaskEpisodeApplicationService.class);
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var finished=new CountDownLatch(1);
        when(service.replay(8)).thenAnswer(call -> {
            entered.countDown();
            try { assertTrue(release.await(5,TimeUnit.SECONDS)); return 0; }
            finally { finished.countDown(); }
        });
        var job=new OpsTaskEpisodeJob(service);
        var caller=Executors.newSingleThreadExecutor();
        try {
            caller.submit(job::replay).get(1,TimeUnit.SECONDS);
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            for (int i=0;i<5;i++) caller.submit(job::replay).get(1,TimeUnit.SECONDS);
            assertEquals(1,finished.getCount(),"The background work is still pending while callers have returned");
            verify(service,times(1)).replay(8);
            release.countDown(); assertTrue(finished.await(1,TimeUnit.SECONDS));
        } finally { release.countDown(); caller.shutdownNow(); job.close(); }
    }
}
