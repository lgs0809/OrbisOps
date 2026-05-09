package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.skill.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class OpsSkillCanaryReviewJob {
    private static final Logger LOG=LoggerFactory.getLogger(OpsSkillCanaryReviewJob.class);
    private final SkillCanaryReviewApplicationService service;
    @Value("${orbisops.skill-evolution.enabled:true}") private boolean enabled=true;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"skill-canary-review");thread.setDaemon(true);return thread;
    });
    public OpsSkillCanaryReviewJob(SkillCanaryReviewPort store,SkillCanaryReviewModelPort model) {
        service=new SkillCanaryReviewApplicationService(store,model,Clock.systemUTC());
    }
    @Scheduled(fixedDelayString="${orbisops.skill-evolver.canary-review-delay-ms:5000}",initialDelayString="${orbisops.skill-evolver.canary-review-initial-delay-ms:20000}")
    public void replay() {
        if(!enabled || !running.compareAndSet(false,true)) return;
        worker.execute(()->{
            try {service.replay(4);}
            catch(RuntimeException error) {LOG.warn("Canary review deferred: {}",error.getClass().getSimpleName());}
            finally {running.set(false);}
        });
    }
    @PreDestroy public void close() {worker.shutdownNow();}
}
