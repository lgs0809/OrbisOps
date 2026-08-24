package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.episode.BackgroundModelRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded maintenance only: each tick restores at most one eligible job and never calls a model. */
@Component
public class OpsBackgroundModelRecoveryJob {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OpsBackgroundModelRecoveryJob.class);
    private final BackgroundModelRecoveryService service;
    public OpsBackgroundModelRecoveryJob(BackgroundModelRecoveryService service) {this.service=service;}
    @Scheduled(fixedDelayString="${orbisops.background-model-recovery.delay-ms:30000}",initialDelayString="${orbisops.background-model-recovery.initial-delay-ms:60000}")
    public void recover() {
        try {service.recoverNext();}
        catch(RuntimeException unavailable) {LOG.warn("Background model recovery deferred: {}",unavailable.getClass().getSimpleName());}
    }
}
