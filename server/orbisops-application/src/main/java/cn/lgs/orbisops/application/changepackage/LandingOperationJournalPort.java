package cn.lgs.orbisops.application.changepackage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Durable persistence boundary used only to reconcile historical UNKNOWN Landing operation facts. */
public interface LandingOperationJournalPort {

    List<LandingOperationRecoveryCandidate> expiredUnknown(int limit);

    Optional<LandingOperationRecoveryCandidate> lockRecoveryCandidate(String operationRunId);

    boolean claimRecovery(
            String operationRunId,
            long previousStateVersion,
            long stateVersion,
            String workerId,
            LocalDateTime leaseDeadline);

    boolean completeRecovery(
            LandingOperationRecoveryClaim claim,
            boolean succeeded,
            String reasonCode,
            LandingOperationPayload payload);

    List<String> operationIdsForTool(
            String landingRunId,
            String toolsetId,
            String toolName);

    /** Returns the frozen execution identities currently projected onto one Landing attempt. */
    List<LandingOperationExecutionBinding> operationExecutionBindings(String landingRunId);

    boolean completeFromToolExecution(
            String landingRunId,
            String operationId,
            String toolName,
            String executionKey,
            LandingOperationPayload payload);

    boolean leaveRecoveryUnknown(
            LandingOperationRecoveryClaim claim,
            String reasonCode,
            LandingOperationPayload payload);
}
