package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryExecutionPort;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Graph-event, audit, and public recovery status mapping boundary. */
final class OpsWorkSessionRecoveryDecisionReporter {

    private final GraphEventApplicationService graphEventService;
    private final OpsConfigAuditService auditService;

    OpsWorkSessionRecoveryDecisionReporter(
            GraphEventApplicationService graphEventService,
            OpsConfigAuditService auditService) {
        this.graphEventService = graphEventService;
        this.auditService = auditService;
    }

    void report(WorkSessionRecoveryPort.RecoveryDecision decision) {
        String eventType = "RECOVERABLE".equals(decision.status())
                ? "WORK_SESSION_RECOVERABLE"
                : "WORK_SESSION_RECOVERY_REVIEW_REQUIRED";
        graphEventService.publishRunEvent(
                decision.runId(),
                "work-session-recovery",
                eventType,
                decision.status(),
                decision.reasonCode());
        auditService.recordRuntimeEvent(
                decision.projectId(),
                "",
                "system",
                "work-session",
                eventType,
                decision.runId(),
                "MEDIUM",
                decision.status(),
                Map.of(
                        "attemptId", decision.attemptId(),
                        "reasonCode", decision.reasonCode()));
    }

    void report(WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome outcome) {
        String eventType = switch (outcome.status()) {
            case "SCHEDULED" -> "WORK_SESSION_RECOVERY_SCHEDULED";
            case "SKIPPED" -> "WORK_SESSION_RECOVERY_EXECUTION_SKIPPED";
            default -> "WORK_SESSION_RECOVERY_SCHEDULE_FAILED";
        };
        graphEventService.publishRunEvent(
                outcome.runId(),
                "work-session-recovery-execution",
                eventType,
                outcome.status(),
                outcome.reasonCode());
        auditService.recordRuntimeEvent(
                outcome.projectId(),
                "",
                "system",
                "work-session",
                eventType,
                outcome.runId(),
                "SCHEDULED".equals(outcome.status()) ? "MEDIUM" : "HIGH",
                outcome.status(),
                Map.of(
                        "attemptId", outcome.attemptId(),
                        "reasonCode", outcome.reasonCode()));
    }
}
