package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLandingOperationRecoveryServiceTest {

    @Test
    void authoritativeSuccessCompletesUnknownWithoutRedispatch() {
        OpsLandingOperationJournal journal = mock(OpsLandingOperationJournal.class);
        ChangePackageLandingProcessManager packages = mock(ChangePackageLandingProcessManager.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsLandingOperationExecutor executor = spy(authoritativeExecutor(true));
        OpsLandingOperationJournal.RecoveryCandidate candidate = candidate();
        OpsLandingOperationJournal.RecoveryClaim claim = new OpsLandingOperationJournal.RecoveryClaim(
                candidate, 8L, "recovery-worker");
        when(journal.expiredUnknown(anyInt())).thenReturn(List.of(candidate));
        when(journal.claimForReconciliation(eq(candidate.operationRunId()), anyString())).thenReturn(claim);

        Map<String, Object> result = service(journal, packages, audit, executor).recoverOnce(10);

        assertEquals(1, result.get("reconciled"));
        verify(journal).completeReconciliation(claim, true, "LANDING_RECONCILED_SUCCEEDED",
                Map.of("status", "SUCCEEDED", "authoritative", true,
                        "executionKey", candidate.executionKey(), "resultId", "remote-result-1"));
        verify(packages).reconcile(candidate.landingRunId(), "landing-recovery");
    }

    @Test
    void untrustedOutcomeRemainsUnknownAndCannotFinalizePackage() {
        OpsLandingOperationJournal journal = mock(OpsLandingOperationJournal.class);
        ChangePackageLandingProcessManager packages = mock(ChangePackageLandingProcessManager.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsLandingOperationExecutor executor = spy(authoritativeExecutor(false));
        OpsLandingOperationJournal.RecoveryCandidate candidate = candidate();
        OpsLandingOperationJournal.RecoveryClaim claim = new OpsLandingOperationJournal.RecoveryClaim(
                candidate, 8L, "recovery-worker");
        when(journal.expiredUnknown(anyInt())).thenReturn(List.of(candidate));
        when(journal.claimForReconciliation(eq(candidate.operationRunId()), anyString())).thenReturn(claim);

        Map<String, Object> result = service(journal, packages, audit, executor).recoverOnce(10);

        assertEquals(1, result.get("unresolved"));
        verify(journal).leaveReconciliationUnknown(claim, "LANDING_RECONCILIATION_UNTRUSTED_RESULT",
                Map.of("status", "SUCCEEDED", "authoritative", false,
                        "executionKey", candidate.executionKey(), "resultId", "remote-result-1"));
        verify(packages, never()).reconcile(anyString(), anyString());
    }

    @Test
    void keepsPublicFourArgumentConstructorAndReturnsEmptyBatchSummary() {
        OpsLandingOperationJournal journal = mock(OpsLandingOperationJournal.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsLandingOperationExecutor> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.empty());
        when(journal.expiredUnknown(5)).thenReturn(List.of());
        OpsLandingOperationRecoveryService service =
                new OpsLandingOperationRecoveryService(
                        journal,
                        mock(ChangePackageLandingProcessManager.class),
                        mock(OpsConfigAuditService.class),
                        provider);

        Map<String, Object> result = service.recoverOnce(5);

        assertTrue(service.isEnabled());
        assertEquals(0, result.get("scanned"));
        assertEquals(0, result.get("reconciled"));
        assertEquals(0, result.get("unresolved"));
        verify(journal).expiredUnknown(5);
    }

    @SuppressWarnings("unchecked")
    private OpsLandingOperationRecoveryService service(
            OpsLandingOperationJournal journal,
            ChangePackageLandingProcessManager packages,
            OpsConfigAuditService audit,
            OpsLandingOperationExecutor executor) {
        ObjectProvider<OpsLandingOperationExecutor> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.of(executor));
        return new OpsLandingOperationRecoveryService(
                journal, packages, audit, provider);
    }

    private OpsLandingOperationExecutor authoritativeExecutor(boolean authoritative) {
        return new OpsLandingOperationExecutor() {
            @Override
            public String adapterType() {
                return "TEST";
            }

            @Override
            public Capabilities capabilities(Map<String, Object> operation) {
                return new Capabilities(true);
            }

            @Override
            public Map<String, Object> reconcile(
                    Map<String, Object> operation,
                    OpsLandingExecutionContext context) {
                return Map.of(
                        "status", "SUCCEEDED",
                        "authoritative", authoritative,
                        "executionKey", context.executionKey(),
                        "resultId", "remote-result-1");
            }
        };
    }

    private OpsLandingOperationJournal.RecoveryCandidate candidate() {
        return new OpsLandingOperationJournal.RecoveryCandidate(
                "operation-run-1",
                "landing-run-1",
                "package-1",
                "project-1",
                3,
                "package-hash-3",
                "operation-1",
                "operation-hash-1",
                "execution-key-1",
                "TEST",
                "toolset-1",
                "tool-1",
                "resource-1",
                "READ_EXTERNAL_STATE",
                7L,
                4L,
                Map.of(
                        "operationId", "operation-1",
                        "operationHash", "operation-hash-1",
                        "adapterType", "TEST",
                        "effectType", "READ_EXTERNAL_STATE",
                        "writesTargetResource", false));
    }
}
