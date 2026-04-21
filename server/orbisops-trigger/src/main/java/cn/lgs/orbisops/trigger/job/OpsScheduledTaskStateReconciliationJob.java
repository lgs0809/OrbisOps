package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCatalogPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Synchronizes inspection history after the separately governed approval/resume flow. */
@Component
public class OpsScheduledTaskStateReconciliationJob {
    private final ScheduledTaskExecutionCatalogPort catalog;
    public OpsScheduledTaskStateReconciliationJob(ScheduledTaskExecutionCatalogPort catalog) {
        this.catalog = catalog;
    }
    @Scheduled(fixedDelayString = "${orbisops.schedules.state-sync-delay-ms:10000}", initialDelay = 10000)
    public void reconcile() { catalog.reconcileWaitingRuns(); }
}
