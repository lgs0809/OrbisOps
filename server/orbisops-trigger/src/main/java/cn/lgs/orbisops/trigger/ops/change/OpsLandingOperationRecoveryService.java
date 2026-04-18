package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reconciles expired UNKNOWN operations without redispatching approved writes. */
@Service
public class OpsLandingOperationRecoveryService {

    private final OpsLandingOperationJournal journal;
    private final OpsLandingOperationExecutorCatalog executors;
    private final OpsLandingRecoveryContextFactory contextFactory;
    private final OpsLandingRecoveryPostCheckVerifier postCheckVerifier;
    private final OpsLandingRecoveryOutcomeReporter outcomeReporter;
    private final OpsLandingRecoverySettings settings;

    public OpsLandingOperationRecoveryService(
            OpsLandingOperationJournal journal,
            ChangePackageLandingProcessManager landingProcessManager,
            OpsConfigAuditService auditService,
            ObjectProvider<OpsLandingOperationExecutor> executorProvider) {
        this(
                journal,
                landingProcessManager,
                auditService,
                executorProvider,
                OpsLandingRecoverySettings.defaults());
    }

    @Autowired
    public OpsLandingOperationRecoveryService(
            OpsLandingOperationJournal journal,
            ChangePackageLandingProcessManager landingProcessManager,
            OpsConfigAuditService auditService,
            ObjectProvider<OpsLandingOperationExecutor> executorProvider,
            OpsLandingRecoverySettings settings) {
        this.journal = journal;
        this.executors = new OpsLandingOperationExecutorCatalog(executorProvider);
        this.contextFactory = new OpsLandingRecoveryContextFactory();
        this.postCheckVerifier = new OpsLandingRecoveryPostCheckVerifier();
        this.outcomeReporter = new OpsLandingRecoveryOutcomeReporter(
                journal,
                landingProcessManager,
                auditService);
        this.settings = settings == null
                ? OpsLandingRecoverySettings.defaults()
                : settings;
    }

    OpsLandingOperationRecoveryService(
            OpsLandingOperationJournal journal,
            OpsLandingOperationExecutorCatalog executors,
            OpsLandingRecoveryContextFactory contextFactory,
            OpsLandingRecoveryPostCheckVerifier postCheckVerifier,
            OpsLandingRecoveryOutcomeReporter outcomeReporter,
            OpsLandingRecoverySettings settings) {
        this.journal = journal;
        this.executors = executors;
        this.contextFactory = contextFactory;
        this.postCheckVerifier = postCheckVerifier;
        this.outcomeReporter = outcomeReporter;
        this.settings = settings == null
                ? OpsLandingRecoverySettings.defaults()
                : settings;
    }

    @Scheduled(fixedDelayString = "${orbisops.approved-landing.recovery.fixed-delay-ms:30000}")
    public void recoverExpiredUnknown() {
        if (settings.enabled()) {
            recoverOnce(settings.batchSize());
        }
    }

    public Map<String, Object> recoverOnce(int limit) {
        int scanned = 0;
        int reconciled = 0;
        int unresolved = 0;
        for (OpsLandingOperationJournal.RecoveryCandidate candidate
                : journal.expiredUnknown(limit)) {
            scanned++;
            OpsLandingOperationJournal.RecoveryClaim claim =
                    journal.claimForReconciliation(
                            candidate.operationRunId(),
                            contextFactory.workerId());
            if (claim == null) {
                continue;
            }
            if (recoverClaim(claim)) {
                reconciled++;
            } else {
                unresolved++;
            }
        }
        return Map.of(
                "scanned", scanned,
                "reconciled", reconciled,
                "unresolved", unresolved);
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    private boolean recoverClaim(
            OpsLandingOperationJournal.RecoveryClaim claim) {
        OpsLandingOperationJournal.RecoveryCandidate candidate = claim.candidate();
        Map<String, Object> operation = candidate.operation();
        OpsLandingOperationExecutor executor = executors.find(candidate.adapterType());
        OpsLandingOperationExecutor.Capabilities capabilities = executor == null
                ? null
                : executor.capabilities(operation);
        if (executor == null
                || operation.isEmpty()
                || capabilities == null
                || !capabilities.reconciliation()) {
            return outcomeReporter.unresolved(
                    claim,
                    "LANDING_RECONCILIATION_EXECUTOR_NOT_CONFIGURED",
                    Map.of(
                            "adapterType", candidate.adapterType(),
                            "operationSnapshotPresent", !operation.isEmpty()));
        }

        OpsLandingExecutionContext context = contextFactory.create(
                candidate,
                settings.reconciliationTimeoutSeconds());
        Map<String, Object> outcome;
        try {
            outcome = executor.reconcile(operation, context);
        } catch (RuntimeException error) {
            return outcomeReporter.unresolved(
                    claim,
                    "LANDING_RECONCILIATION_QUERY_FAILED",
                    Map.of("error", text(error.getMessage())));
        }
        if (outcome == null) {
            outcome = Map.of();
        }
        String status = text(outcome.get("status")).toUpperCase(Locale.ROOT);
        boolean authoritative = Boolean.TRUE.equals(outcome.get("authoritative"));
        String returnedKey = text(outcome.get("executionKey"));
        if (!authoritative || !candidate.executionKey().equals(returnedKey)) {
            return outcomeReporter.unresolved(
                    claim,
                    "LANDING_RECONCILIATION_UNTRUSTED_RESULT",
                    outcome);
        }

        if (List.of("SUCCEEDED", "PASSED", "COMPLETED").contains(status)) {
            Map<String, Object> postCheck = postCheckVerifier.verify(
                    operation,
                    executor,
                    context.asMap());
            if (!postCheck.isEmpty()) {
                return outcomeReporter.complete(
                        claim,
                        false,
                        text(postCheck.get("reasonCode")),
                        postCheck);
            }
            return outcomeReporter.complete(
                    claim,
                    true,
                    "LANDING_RECONCILED_SUCCEEDED",
                    outcome);
        }
        if (List.of("FAILED", "REJECTED", "NOT_EXECUTED").contains(status)) {
            return outcomeReporter.complete(
                    claim,
                    false,
                    textOr(outcome.get("reasonCode"), "LANDING_RECONCILED_FAILED"),
                    outcome);
        }
        return outcomeReporter.unresolved(
                claim,
                "LANDING_RECONCILIATION_RESULT_UNKNOWN",
                outcome);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOr(Object value, String fallback) {
        String valueText = text(value);
        return StringUtils.hasText(valueText) ? valueText : fallback;
    }
}
