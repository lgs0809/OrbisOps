package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.incident.AlertCorrelationApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "orbisops.incidents.correlation.enabled", havingValue = "true", matchIfMissing = true)
public class OpsAlertCorrelationJob {
    private final AlertCorrelationApplicationService correlation;
    public OpsAlertCorrelationJob(AlertCorrelationApplicationService correlation) { this.correlation = correlation; }
    @Scheduled(fixedDelayString = "${orbisops.incidents.correlation.poll-ms:5000}", initialDelay = 10000)
    public void correlate() {
        try { correlation.reconcile(100); }
        catch (RuntimeException error) {
            log.warn("Automatic alert correlation will retry retained source events: {}", error.getClass().getSimpleName());
        }
    }
}
