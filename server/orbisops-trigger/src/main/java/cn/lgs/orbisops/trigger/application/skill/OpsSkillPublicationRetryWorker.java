package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/** Dedicated background executor: slow model review never occupies request or main scheduling threads. */
@Component
public class OpsSkillPublicationRetryWorker {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OpsSkillPublicationRetryWorker.class);
    private final SkillPublicationRetryPort retries;
    private final SkillReleaseApplicationService releases;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> {
        var t=new Thread(r,"skill-publication-retry");t.setDaemon(true);return t;
    });
    public OpsSkillPublicationRetryWorker(SkillPublicationRetryPort retries,SkillReleaseApplicationService releases) {
        this.retries=retries;this.releases=releases;
    }
    @PostConstruct void start() {worker.scheduleWithFixedDelay(this::runOnce,5,10,TimeUnit.SECONDS);}
    @PreDestroy void stop() {worker.shutdownNow();}
    public void runOnce() {
        try {
            retries.claim().ifPresent(claim -> {
                try {retries.complete(claim,releases.resumeRetained(claim.candidateId(),()->retries.requireCurrent(claim)));}
                catch(SkillContentReviewUnavailableException transientFailure) {retries.defer(claim,"SKILL_CONTENT_REVIEW_UNAVAILABLE");}
                catch(RuntimeException failure) {
                    LOG.warn("Retained Skill publication needs recheck: candidate={}, type={}, location={}",
                        claim.candidateId(),failure.getClass().getSimpleName(),
                        failure.getStackTrace().length==0?"unknown":failure.getStackTrace()[0]);
                    retries.defer(claim,"SKILL_PUBLICATION_RECHECK_REQUIRED");
                }
            });
        } catch(RuntimeException unavailable) {
            // The durable lease/row remains authoritative; reconnect on the next tick after a DB outage.
        }
    }
}
