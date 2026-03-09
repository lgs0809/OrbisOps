package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Audit, journal transition, and parent landing reconciliation boundary. */
final class OpsLandingRecoveryOutcomeReporter {

    private final OpsLandingOperationJournal journal;
    private final ChangePackageLandingProcessManager landingProcessManager;
    private final OpsConfigAuditService auditService;

    OpsLandingRecoveryOutcomeReporter(
            OpsLandingOperationJournal journal,
            ChangePackageLandingProcessManager landingProcessManager,
            OpsConfigAuditService auditService) {
        this.journal = journal;
        this.landingProcessManager = landingProcessManager;
        this.auditService = auditService;
    }

    boolean complete(
            OpsLandingOperationJournal.RecoveryClaim claim,
            boolean succeeded,
            String reasonCode,
            Map<String, Object> result) {
        OpsLandingOperationJournal.RecoveryCandidate candidate = claim.candidate();
        auditService.recordRuntimeEvent(
                candidate.projectId(),
                "landing-runtime",
                "system",
                "approved-landing",
                succeeded
                        ? "LANDING_OPERATION_RECONCILED"
                        : "LANDING_OPERATION_RECONCILED_FAILED",
                candidate.operationRunId(),
                "HIGH",
                succeeded ? "SUCCEEDED" : "FAILED",
                Map.of(
                        "landingRunId", candidate.landingRunId(),
                        "operationId", candidate.operationId(),
                        "executionKey", candidate.executionKey(),
                        "reasonCode", reasonCode));
        journal.completeReconciliation(claim, succeeded, reasonCode, result);
        landingProcessManager.reconcile(
                candidate.landingRunId(),
                "landing-recovery");
        return true;
    }

    boolean unresolved(
            OpsLandingOperationJournal.RecoveryClaim claim,
            String reasonCode,
            Object result) {
        OpsLandingOperationJournal.RecoveryCandidate candidate = claim.candidate();
        auditService.recordRuntimeEvent(
                candidate.projectId(),
                "landing-runtime",
                "system",
                "approved-landing",
                "LANDING_OPERATION_RECONCILIATION_REQUIRED",
                candidate.operationRunId(),
                "HIGH",
                "BLOCKED",
                Map.of(
                        "landingRunId", candidate.landingRunId(),
                        "operationId", candidate.operationId(),
                        "executionKey", candidate.executionKey(),
                        "reasonCode", reasonCode));
        journal.leaveReconciliationUnknown(claim, reasonCode, result);
        return false;
    }
}
