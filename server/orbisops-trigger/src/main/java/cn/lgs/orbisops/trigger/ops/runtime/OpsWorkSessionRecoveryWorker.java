package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.WorkSessionProcessManager;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically recovers expired durable work-session leases. */
@Component
public class OpsWorkSessionRecoveryWorker {

    private final WorkSessionProcessManager processManager;
    private final OpsWorkSessionRecoveryDecisionReporter reporter;
    private final OpsWorkSessionRecoverySettings settings;

    public OpsWorkSessionRecoveryWorker(
            WorkSessionProcessManager processManager,
            GraphEventApplicationService graphEventService,
            OpsConfigAuditService auditService) {
        this(
                processManager,
                graphEventService,
                auditService,
                OpsWorkSessionRecoverySettings.defaults());
    }

    @Autowired
    public OpsWorkSessionRecoveryWorker(
            WorkSessionProcessManager processManager,
            GraphEventApplicationService graphEventService,
            OpsConfigAuditService auditService,
            OpsWorkSessionRecoverySettings settings) {
        this(
                processManager,
                new OpsWorkSessionRecoveryDecisionReporter(
                        graphEventService,
                        auditService),
                settings);
    }

    OpsWorkSessionRecoveryWorker(
            WorkSessionProcessManager processManager,
            OpsWorkSessionRecoveryDecisionReporter reporter,
            OpsWorkSessionRecoverySettings settings) {
        this.processManager = processManager;
        this.reporter = reporter;
        this.settings = settings == null
                ? OpsWorkSessionRecoverySettings.defaults()
                : settings;
    }

    @Scheduled(fixedDelayString = "${orbisops.work-session.recovery.interval-ms:30000}")
    public void recoverExpiredRuns() {
        if (!settings.enabled()) {
            return;
        }
        WorkSessionProcessManager.RecoveryBatchOutcome outcome =
                processManager.recoverAndExecuteExpiredLeases(settings.batchSize());
        for (WorkSessionRecoveryPort.RecoveryDecision decision : outcome.decisions()) {
            reporter.report(decision);
        }
        for (var execution : outcome.executions()) {
            reporter.report(execution);
        }
    }
}
