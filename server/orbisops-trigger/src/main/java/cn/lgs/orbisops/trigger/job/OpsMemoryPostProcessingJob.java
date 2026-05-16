package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.memory.MemoryPostProcessingApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded recovery scan, including work committed before dispatch or abandoned by an expired worker. */
@Component
public class OpsMemoryPostProcessingJob {
    private static final Logger LOG = LoggerFactory.getLogger(OpsMemoryPostProcessingJob.class);
    private final MemoryPostProcessingApplicationService service;

    public OpsMemoryPostProcessingJob(MemoryPostProcessingApplicationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${orbisops.chat.memory.replay-delay-ms:5000}", initialDelayString = "${orbisops.chat.memory.replay-delay-ms:5000}")
    public void replay() {
        try {
            service.replayPending(32);
        } catch (RuntimeException error) {
            LOG.warn("Memory outbox scan failed: {}", error.getClass().getSimpleName());
        }
    }
}
