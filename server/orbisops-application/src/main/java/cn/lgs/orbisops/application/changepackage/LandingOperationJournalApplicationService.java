package cn.lgs.orbisops.application.changepackage;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Application boundary used only to reconcile historical UNKNOWN Landing operation facts. */
public class LandingOperationJournalApplicationService {

    private final LandingOperationJournalPort journalPort;
    private final ChangePackageTransactionPort transactionPort;
    private final LandingOperationJournalSettings settings;
    private final Clock clock;

    public LandingOperationJournalApplicationService(
            LandingOperationJournalPort journalPort,
            ChangePackageTransactionPort transactionPort,
            LandingOperationJournalSettings settings,
            Clock clock) {
        if (journalPort == null) throw new IllegalArgumentException("LANDING_OPERATION_JOURNAL_PORT_REQUIRED");
        if (transactionPort == null) throw new IllegalArgumentException("CHANGE_PACKAGE_TRANSACTION_PORT_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("LANDING_OPERATION_JOURNAL_SETTINGS_REQUIRED");
        this.journalPort = journalPort;
        this.transactionPort = transactionPort;
        this.settings = settings;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public List<LandingOperationRecoveryCandidate> expiredUnknown(int limit) {
        return journalPort.expiredUnknown(Math.max(1, Math.min(limit, 100)));
    }

    public Optional<LandingOperationRecoveryClaim> claimForReconciliation(
            String operationRunId,
            String workerId) {
        return transactionPort.required(() -> {
            Optional<LandingOperationRecoveryCandidate> locked =
                    journalPort.lockRecoveryCandidate(operationRunId);
            if (locked.isEmpty()) return Optional.empty();
            LandingOperationRecoveryCandidate candidate = locked.get();
            long nextStateVersion = candidate.stateVersion() + 1L;
            if (!journalPort.claimRecovery(
                    operationRunId,
                    candidate.stateVersion(),
                    nextStateVersion,
                    text(workerId),
                    leaseDeadline())) {
                return Optional.empty();
            }
            return Optional.of(new LandingOperationRecoveryClaim(
                    candidate,
                    nextStateVersion,
                    text(workerId)));
        });
    }

    public void completeReconciliation(
            LandingOperationRecoveryClaim claim,
            boolean succeeded,
            String reasonCode,
            LandingOperationPayload payload) {
        requireRecovery(journalPort.completeRecovery(
                claim,
                succeeded,
                text(reasonCode),
                safe(payload)), claim);
    }

    public List<String> operationIdsForTool(
            String landingRunId,
            String toolsetId,
            String toolName) {
        return journalPort.operationIdsForTool(
                text(landingRunId), text(toolsetId), text(toolName));
    }

    public List<LandingOperationExecutionBinding> operationExecutionBindings(String landingRunId) {
        return journalPort.operationExecutionBindings(text(landingRunId));
    }

    public void completeFromToolExecution(
            String landingRunId,
            String operationId,
            String toolName,
            String executionKey,
            LandingOperationPayload payload) {
        if (!journalPort.completeFromToolExecution(
                text(landingRunId),
                text(operationId),
                text(toolName),
                text(executionKey),
                safe(payload))) {
            throw new IllegalStateException(
                    "LANDING_TOOL_EXECUTION_PROJECTION_CAS_FAILED：operation=" + text(operationId));
        }
    }

    public void leaveReconciliationUnknown(
            LandingOperationRecoveryClaim claim,
            String reasonCode,
            LandingOperationPayload payload) {
        requireRecovery(journalPort.leaveRecoveryUnknown(
                claim,
                text(reasonCode),
                safe(payload)), claim);
    }

    public LandingOperationPayload payload(Object raw) {
        return new LandingOperationPayload(
                raw,
                text(value(raw, "remoteRequestId")),
                text(value(raw, "resultId")),
                text(value(raw, "outputHash")));
    }

    private LandingOperationPayload safe(LandingOperationPayload payload) {
        return payload == null ? payload(Map.of()) : payload;
    }

    private LocalDateTime leaseDeadline() {
        return LocalDateTime.now(clock).plusSeconds(settings.leaseSeconds());
    }

    private void requireRecovery(boolean updated, LandingOperationRecoveryClaim claim) {
        if (!updated) {
            throw new IllegalStateException(
                    "LANDING_RECONCILIATION_LEASE_LOST：operation=" + claim.candidate().operationId());
        }
    }

    private Object value(Object source, String key) {
        return source instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
