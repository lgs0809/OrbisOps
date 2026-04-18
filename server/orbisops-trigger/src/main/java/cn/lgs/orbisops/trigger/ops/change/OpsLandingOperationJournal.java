package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.changepackage.LandingOperationRecoveryCandidate;
import cn.lgs.orbisops.application.changepackage.LandingOperationRecoveryClaim;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Trigger facade used only by the historical UNKNOWN reconciliation worker. */
@Service
public class OpsLandingOperationJournal {

    private final LandingOperationJournalApplicationService applicationService;

    public OpsLandingOperationJournal(LandingOperationJournalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public List<RecoveryCandidate> expiredUnknown(int limit) {
        return applicationService.expiredUnknown(limit).stream()
                .map(this::recoveryCandidate)
                .toList();
    }

    public RecoveryClaim claimForReconciliation(String operationRunId, String workerId) {
        return applicationService.claimForReconciliation(operationRunId, workerId)
                .map(this::recoveryClaim)
                .orElse(null);
    }

    public void completeReconciliation(
            RecoveryClaim claim,
            boolean succeeded,
            String reasonCode,
            Map<String, Object> result) {
        applicationService.completeReconciliation(
                recoveryClaim(claim),
                succeeded,
                reasonCode,
                applicationService.payload(result));
    }

    public void leaveReconciliationUnknown(
            RecoveryClaim claim,
            String reasonCode,
            Object result) {
        applicationService.leaveReconciliationUnknown(
                recoveryClaim(claim),
                reasonCode,
                applicationService.payload(result));
    }

    private RecoveryCandidate recoveryCandidate(LandingOperationRecoveryCandidate candidate) {
        return new RecoveryCandidate(
                candidate.operationRunId(),
                candidate.landingRunId(),
                candidate.packageId(),
                candidate.projectId(),
                candidate.approvedVersion(),
                candidate.approvedPackageHash(),
                candidate.operationId(),
                candidate.operationHash(),
                candidate.executionKey(),
                candidate.adapterType(),
                candidate.toolsetId(),
                candidate.toolName(),
                candidate.resourceKey(),
                candidate.effectType(),
                candidate.stateVersion(),
                candidate.fencingToken(),
                candidate.operation());
    }

    private LandingOperationRecoveryCandidate recoveryCandidate(RecoveryCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("LANDING_RECOVERY_CANDIDATE_REQUIRED");
        return new LandingOperationRecoveryCandidate(
                candidate.operationRunId(),
                candidate.landingRunId(),
                candidate.packageId(),
                candidate.projectId(),
                candidate.approvedVersion(),
                candidate.approvedPackageHash(),
                candidate.operationId(),
                candidate.operationHash(),
                candidate.executionKey(),
                candidate.adapterType(),
                candidate.toolsetId(),
                candidate.toolName(),
                candidate.resourceKey(),
                candidate.effectType(),
                candidate.stateVersion(),
                candidate.fencingToken(),
                candidate.operation());
    }

    private RecoveryClaim recoveryClaim(LandingOperationRecoveryClaim claim) {
        return new RecoveryClaim(
                recoveryCandidate(claim.candidate()),
                claim.stateVersion(),
                claim.workerId());
    }

    private LandingOperationRecoveryClaim recoveryClaim(RecoveryClaim claim) {
        if (claim == null) throw new IllegalArgumentException("LANDING_RECOVERY_CLAIM_REQUIRED");
        return new LandingOperationRecoveryClaim(
                recoveryCandidate(claim.candidate()),
                claim.stateVersion(),
                claim.workerId());
    }

    public record RecoveryCandidate(
            String operationRunId,
            String landingRunId,
            String packageId,
            String projectId,
            int approvedVersion,
            String approvedPackageHash,
            String operationId,
            String operationHash,
            String executionKey,
            String adapterType,
            String toolsetId,
            String toolName,
            String resourceKey,
            String effectType,
            long stateVersion,
            long fencingToken,
            Map<String, Object> operation) {
    }

    public record RecoveryClaim(
            RecoveryCandidate candidate,
            long stateVersion,
            String workerId) {
    }
}
