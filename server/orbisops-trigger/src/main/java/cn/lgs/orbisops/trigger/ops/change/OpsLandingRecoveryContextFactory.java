package cn.lgs.orbisops.trigger.ops.change;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Worker identity and reconciliation-only execution context factory. */
final class OpsLandingRecoveryContextFactory {

    String workerId() {
        return "landing-recovery-" + UUID.randomUUID();
    }

    OpsLandingExecutionContext create(
            OpsLandingOperationJournal.RecoveryCandidate candidate,
            int timeoutSeconds) {
        return new OpsLandingExecutionContext(
                candidate.executionKey(),
                candidate.fencingToken(),
                candidate.packageId(),
                candidate.approvedVersion(),
                candidate.approvedPackageHash(),
                candidate.operationId(),
                candidate.operationHash(),
                "landing-recovery",
                Instant.now().plusSeconds(timeoutSeconds),
                Map.of(
                        "projectId", candidate.projectId(),
                        "landingRunId", candidate.landingRunId(),
                        "reconciliationOnly", true));
    }
}
