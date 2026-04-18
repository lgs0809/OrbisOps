package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingCompletion;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangePackageLandingProcessManagerTest {

    private IChangePackageCurrentRepository currentRepository;
    private IChangePackageVersionRepository versionRepository;
    private IChangePackagePointerRepository pointerRepository;
    private IChangePackageEventRepository eventRepository;
    private ChangePackageLandingGatePort gatePort;
    private ChangePackageLandingRunPort runPort;
    private ChangePackageLandingJournalPort journalPort;
    private ChangePackageLandingLockPort lockPort;
    private ChangePackageLandingRuntimePort runtimePort;
    private ChangePackageAuditPort auditPort;
    private ChangePackageLandingSignalPort signalPort;
    private ToolExecutionIdempotencyPort toolExecutionLedger;
    private ChangePackageLandingProcessManager manager;

    @BeforeEach
    void setUp() {
        currentRepository = mock(IChangePackageCurrentRepository.class);
        versionRepository = mock(IChangePackageVersionRepository.class);
        pointerRepository = mock(IChangePackagePointerRepository.class);
        eventRepository = mock(IChangePackageEventRepository.class);
        gatePort = mock(ChangePackageLandingGatePort.class);
        runPort = mock(ChangePackageLandingRunPort.class);
        journalPort = mock(ChangePackageLandingJournalPort.class);
        lockPort = mock(ChangePackageLandingLockPort.class);
        runtimePort = mock(ChangePackageLandingRuntimePort.class);
        auditPort = mock(ChangePackageAuditPort.class);
        signalPort = mock(ChangePackageLandingSignalPort.class);
        toolExecutionLedger = mock(ToolExecutionIdempotencyPort.class);
        when(currentRepository.available()).thenReturn(true);
        when(versionRepository.available()).thenReturn(true);
        when(pointerRepository.available()).thenReturn(true);
        when(eventRepository.available()).thenReturn(true);
        when(journalPort.available()).thenReturn(true);
        when(runPort.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(lockPort.acquire(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(List.of("project-1:config:x"));
        manager = new ChangePackageLandingProcessManager(
                currentRepository, versionRepository, pointerRepository, eventRepository,
                gatePort, runPort, journalPort, lockPort, runtimePort, auditPort, signalPort,
                toolExecutionLedger);
    }

    @Test
    void requestMustMatchFrozenApprovedVersionAndHash() {
        stubApproved();

        assertThrows(IllegalStateException.class,
                () -> manager.land(new ChangePackageCommands.Land("cp-1",
                        Map.of("version", 1, "packageHash", "stale"), "alice")));

        verify(runPort, never()).start(any(), any(), any(), anyInt(), any(), any(), any());
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void successfulLandingPersistsIntentBeforeRuntimeAndClosesAggregate() {
        ChangePackageCurrent current = stubApproved();
        when(pointerRepository.compareAndSetLandingStarted(eq(current.pointer()), anyString())).thenReturn(true);
        when(runtimePort.execute(eq(current), any(), any(), any(), anyString(), eq("alice")))
                .thenReturn(new ChangePackageLandingRuntimeResult(
                        ChangePackageStatus.LANDED,
                        "LANDING_SUCCEEDED",
                        "",
                        "done",
                        true,
                        Map.of()));
        when(pointerRepository.compareAndSetLandingResult(
                any(), any(ChangePackageLandingCompletion.class))).thenReturn(true);

        ChangePackageLandingOutcome outcome = manager.land(landCommand());

        assertEquals(ChangePackageStatus.LANDED, outcome.status());
        assertTrue(!outcome.idempotentReplay());
        InOrder order = inOrder(runPort, journalPort, lockPort, pointerRepository,
                eventRepository, runtimePort, auditPort, signalPort);
        order.verify(runPort).start(anyString(), eq("landing:cp-1:1:hash-1"), eq(current),
                eq(1), eq("hash-1"), anyString(), eq("alice"));
        order.verify(journalPort).initialize(anyString(), any(ChangePackageLandingPlan.class));
        order.verify(lockPort).acquire(anyString(), anyString(), eq(current), any(), eq("alice"));
        order.verify(pointerRepository).compareAndSetLandingStarted(eq(current.pointer()), anyString());
        order.verify(eventRepository).append(any(ChangePackageEvent.class));
        order.verify(runtimePort).execute(eq(current), any(), any(), any(ChangePackageLandingRequest.class),
                anyString(), eq("alice"));
        order.verify(journalPort).completeFromRuntimeResult(anyString(), any(), any());
        order.verify(runPort).complete(anyString(), eq(ChangePackageLandingRunStatus.SUCCEEDED), any());
        order.verify(pointerRepository).compareAndSetLandingResult(any(),
                org.mockito.ArgumentMatchers.argThat(completion ->
                        completion.status() == ChangePackageStatus.LANDED
                                && completion.failureSummary().isEmpty()
                                && "LANDED".equals(completion.result().get("status"))));
        order.verify(eventRepository).append(any(ChangePackageEvent.class));
        order.verify(auditPort).record(eq("project-1"), eq("land"), eq("cp-1"), eq(current), any());
        order.verify(signalPort).recordOutcome(eq(current), any(), anyString(), eq("LANDED"), any());
        verify(lockPort).release(any(), anyString());
    }

    @Test
    void successfulIdempotentReplayDoesNotDispatchRuntime() {
        ChangePackageCurrent current = stubApproved();
        when(runPort.findByIdempotencyKey("landing:cp-1:1:hash-1"))
                .thenReturn(Optional.of(new ChangePackageLandingRun(
                        "lr-done", "cp-1", "project-1", 1, "hash-1",
                        "landing:cp-1:1:hash-1", ChangePackageLandingRunStatus.SUCCEEDED, false, Map.of())));

        ChangePackageLandingOutcome outcome = manager.land(landCommand());

        assertTrue(outcome.idempotentReplay());
        assertEquals(ChangePackageStatus.LANDED, outcome.status());
        verify(runPort, never()).start(any(), any(), any(), anyInt(), any(), any(), any());
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
        verify(eventRepository).append(any(ChangePackageEvent.class));
        verify(auditPort).record(eq("project-1"), eq("landing-idempotent-replay"),
                eq("cp-1"), eq(current), any());
    }

    @Test
    void legacyCustomIdempotencyKeyStillReusesExactFrozenPackageRun() {
        ChangePackageCurrent current = stubApproved();
        ChangePackageCommands.Land command = customLandCommand("legacy-key");
        ChangePackageLandingRequest request = ChangePackageLandingRequest.from(
                current.pointer(), command.request());
        String scopedKey = request.effectiveIdempotencyKey(current.packageId());
        when(runPort.findByIdempotencyKey(scopedKey)).thenReturn(Optional.empty());
        when(runPort.findByIdempotencyKey("legacy-key"))
                .thenReturn(Optional.of(new ChangePackageLandingRun(
                        "lr-legacy", "cp-1", "project-1", 1, "hash-1",
                        "legacy-key", ChangePackageLandingRunStatus.SUCCEEDED, false, Map.of())));

        ChangePackageLandingOutcome outcome = manager.land(command);

        assertTrue(outcome.idempotentReplay());
        assertEquals("lr-legacy", outcome.landingRunId());
        verify(runPort, never()).start(any(), any(), any(), anyInt(), any(), any(), any());
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void foreignLegacyCustomKeyCannotMasqueradeAsIdempotentReplay() {
        ChangePackageCurrent current = stubApproved();
        ChangePackageCommands.Land command = customLandCommand("shared-key");
        ChangePackageLandingRequest request = ChangePackageLandingRequest.from(
                current.pointer(), command.request());
        String scopedKey = request.effectiveIdempotencyKey(current.packageId());
        when(runPort.findByIdempotencyKey(scopedKey)).thenReturn(Optional.empty());
        when(runPort.findByIdempotencyKey("shared-key"))
                .thenReturn(Optional.of(new ChangePackageLandingRun(
                        "lr-foreign", "cp-other", "project-1", 1, "other-hash",
                        "shared-key", ChangePackageLandingRunStatus.SUCCEEDED, false, Map.of())));
        when(pointerRepository.compareAndSetLandingStarted(eq(current.pointer()), anyString())).thenReturn(true);
        when(pointerRepository.compareAndSetLandingResult(any(), any(ChangePackageLandingCompletion.class))).thenReturn(true);
        when(runtimePort.execute(eq(current), any(), any(), any(), anyString(), eq("alice")))
                .thenReturn(new ChangePackageLandingRuntimeResult(
                        ChangePackageStatus.LANDED,
                        "LANDING_SUCCEEDED",
                        "",
                        "done",
                        true,
                        Map.of()));

        ChangePackageLandingOutcome outcome = manager.land(command);

        assertTrue(!outcome.idempotentReplay());
        assertTrue(scopedKey.startsWith("landing:custom:"));
        verify(runPort).start(anyString(), eq(scopedKey), eq(current),
                eq(1), eq("hash-1"), anyString(), eq("alice"));
        verify(runtimePort).execute(eq(current), any(), any(), any(), anyString(), eq("alice"));
    }

    @Test
    void activeRunRejectsDuplicateProductionWrite() {
        stubApproved();
        when(runPort.findByIdempotencyKey("landing:cp-1:1:hash-1"))
                .thenReturn(Optional.of(new ChangePackageLandingRun(
                        "lr-running", "cp-1", "project-1", 1, "hash-1",
                        "landing:cp-1:1:hash-1", ChangePackageLandingRunStatus.RUNNING, false, Map.of())));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.land(landCommand()));

        assertTrue(error.getMessage().contains("正在执行"));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleFailedRunRequiresReconciliationInsteadOfReplay() {
        stubApproved();
        when(runPort.findByIdempotencyKey("landing:cp-1:1:hash-1"))
                .thenReturn(Optional.of(new ChangePackageLandingRun(
                        "lr-failed", "cp-1", "project-1", 1, "hash-1",
                        "landing:cp-1:1:hash-1", ChangePackageLandingRunStatus.FAILED, true, Map.of())));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.land(landCommand()));

        assertTrue(error.getMessage().contains("REQUIRES_RECONCILIATION"));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void startCasConflictNeverDispatchesRuntimeAndReleasesLock() {
        stubApproved();
        when(pointerRepository.compareAndSetLandingStarted(any(), anyString())).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.land(landCommand()));

        assertTrue(error.getMessage().contains("CAS"));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
        verify(runPort).complete(anyString(), eq(ChangePackageLandingRunStatus.FAILED), any());
        verify(lockPort).release(any(), anyString());
    }

    @Test
    void runtimeFailureRecordsFailedRunAndFailedPackageWithoutFakeSuccess() {
        ChangePackageCurrent current = stubApproved();
        when(pointerRepository.compareAndSetLandingStarted(eq(current.pointer()), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("executor down"))
                .when(runtimePort).execute(eq(current), any(), any(), any(), anyString(), eq("alice"));
        when(pointerRepository.compareAndSetLandingResult(
                any(), any(ChangePackageLandingCompletion.class))).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.land(landCommand()));

        assertTrue(error.getMessage().contains("executor down"));
        verify(runPort).complete(anyString(), eq(ChangePackageLandingRunStatus.FAILED), any());
        verify(pointerRepository).compareAndSetLandingResult(any(),
                org.mockito.ArgumentMatchers.argThat(completion ->
                        completion.status() == ChangePackageStatus.LANDING_FAILED
                                && !completion.failureSummary().isEmpty()));
        verify(signalPort, never()).recordOutcome(any(), any(), any(), any(), any());
        verify(lockPort).release(any(), anyString());
    }

    @Test
    void failedLandingWithProvenPendingOperationsCanRetryWithFreshIdempotencyKey() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDING_FAILED);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version()));
        when(runPort.find("lr-1")).thenReturn(Optional.of(new ChangePackageLandingRun(
                "lr-1", "cp-1", "project-1", 1, "hash-1",
                "landing:custom:old", ChangePackageLandingRunStatus.FAILED, true, Map.of())));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(fact("NONE", "PENDING")));
        when(pointerRepository.compareAndSetLandingStarted(eq(current.pointer()), anyString())).thenReturn(true);
        when(runtimePort.execute(eq(current), any(), any(), any(), anyString(), eq("alice")))
                .thenReturn(new ChangePackageLandingRuntimeResult(
                        ChangePackageStatus.LANDED,
                        "LANDING_SUCCEEDED",
                        "",
                        "done",
                        true,
                        Map.of()));
        when(pointerRepository.compareAndSetLandingResult(
                any(), any(ChangePackageLandingCompletion.class))).thenReturn(true);

        ChangePackageLandingOutcome outcome = manager.land(customLandCommand("retry-after-runtime-fix"));

        assertEquals(ChangePackageStatus.LANDED, outcome.status());
        verify(toolExecutionLedger).hasUnresolvedSideEffect("project-1", "lr-1");
        verify(journalPort).operationFacts("lr-1");
        verify(runtimePort).execute(eq(current), any(), any(), any(), anyString(), eq("alice"));
    }

    @Test
    void failedLandingRetryIsRejectedWhenAnyPreviousOperationCompleted() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDING_FAILED);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version()));
        when(runPort.find("lr-1")).thenReturn(Optional.of(new ChangePackageLandingRun(
                "lr-1", "cp-1", "project-1", 1, "hash-1",
                "landing:custom:old", ChangePackageLandingRunStatus.FAILED, true, Map.of())));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(fact("COMPLETED", "SUCCEEDED")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.land(customLandCommand("retry-after-partial-write")));

        assertTrue(error.getMessage().contains("OPERATIONS_NOT_PROVEN_PENDING"));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
        verify(runPort, never()).start(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void unifiedToolExecutionUnknownKeepsRecoveryOpenBeforeLegacyJournalMutation() {
        ChangePackageLandingRun run = recoveryRun();
        when(runPort.find("lr-1")).thenReturn(Optional.of(run));
        when(toolExecutionLedger.hasUnresolvedSideEffect("project-1", "lr-1")).thenReturn(true);

        manager.reconcile("lr-1", "recovery");

        verify(journalPort, never()).operationFacts("lr-1");
        verify(journalPort, never()).markAllUnexecutedBlocked(any(), any(), any());
        verify(runPort, never()).complete(any(), any(), any());
        verify(pointerRepository, never()).compareAndSetLandingResult(
                any(), any(ChangePackageLandingCompletion.class));
    }

    @Test
    void unknownRecoveryFactsLeaveRunOpen() {
        ChangePackageLandingRun run = recoveryRun();
        when(runPort.find("lr-1")).thenReturn(Optional.of(run));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(
                fact("UNKNOWN", "UNKNOWN")));

        manager.reconcile("lr-1", "recovery");

        verify(runPort, never()).complete(any(), any(), any());
        verify(currentRepository, never()).find(any());
        verify(pointerRepository, never()).compareAndSetLandingResult(
                any(), any(ChangePackageLandingCompletion.class));
    }

    @Test
    void pendingUnexecutedRecoveryClosesLandingFailedWithoutInventingReplan() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDING_RUNNING);
        when(runPort.find("lr-1")).thenReturn(Optional.of(recoveryRun()));
        when(journalPort.operationFacts("lr-1"))
                .thenReturn(List.of(fact("NONE", "PENDING")));
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(pointerRepository.compareAndSetLandingResult(
                eq(current.pointer()), any(ChangePackageLandingCompletion.class))).thenReturn(true);

        manager.reconcile("lr-1", "recovery");

        verify(journalPort, never()).markAllUnexecutedBlocked(any(), any(), any());
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
        verify(runPort).complete(eq("lr-1"), eq(ChangePackageLandingRunStatus.FAILED),
                org.mockito.ArgumentMatchers.argThat(result ->
                        result instanceof Map<?, ?> payload
                                && "LANDING_RECONCILED_UNEXECUTED".equals(payload.get("reasonCode"))
                                && Boolean.FALSE.equals(payload.get("executedProductionAction"))));
        verify(pointerRepository).compareAndSetLandingResult(eq(current.pointer()),
                org.mockito.ArgumentMatchers.argThat(completion ->
                        completion.status() == ChangePackageStatus.LANDING_FAILED
                                && "lr-1".equals(completion.landingRunId())
                                && !completion.failureSummary().isEmpty()));
    }

    @Test
    void completedSuccessfulRecoveryClosesLanded() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDING_RUNNING);
        when(runPort.find("lr-1")).thenReturn(Optional.of(recoveryRun()));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(
                fact("COMPLETED", "SUCCEEDED")));
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version()));
        when(runPort.executionActor("lr-1")).thenReturn("original-operator");
        when(runtimePort.verifyCompletedOperations(eq(current), any(), any(), eq("lr-1"), eq("original-operator"), any()))
                .thenReturn(Map.of("passed", true, "verificationId", "lv-1"));
        when(pointerRepository.compareAndSetLandingResult(
                eq(current.pointer()), any(ChangePackageLandingCompletion.class))).thenReturn(true);

        manager.reconcile("lr-1", "recovery");

        verify(runPort).complete(eq("lr-1"), eq(ChangePackageLandingRunStatus.SUCCEEDED), any());
        verify(pointerRepository).compareAndSetLandingResult(eq(current.pointer()),
                org.mockito.ArgumentMatchers.argThat(completion ->
                        completion.status() == ChangePackageStatus.LANDED
                                && "lr-1".equals(completion.landingRunId())
                                && completion.failureSummary().isEmpty()));
        verify(eventRepository).append(any(ChangePackageEvent.class));
        verify(auditPort).record(eq("project-1"), eq("landing-reconcile"),
                eq("cp-1"), eq(current), any());
    }

    @Test
    void lateSuccessfulCompletionRecoveryPromotesFailedPackageWithoutRedispatch() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDING_FAILED);
        ChangePackageLandingRun failedRun = new ChangePackageLandingRun(
                "lr-1", "cp-1", "project-1", 1, "hash-1",
                "landing:cp-1:1:hash-1", ChangePackageLandingRunStatus.FAILED, true, Map.of());
        when(runPort.findLateCompletedFailedRuns(50)).thenReturn(List.of(failedRun));
        when(runPort.find("lr-1")).thenReturn(Optional.of(failedRun));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(
                fact("COMPLETED", "SUCCEEDED")));
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version()));
        when(runPort.executionActor("lr-1")).thenReturn("original-operator");
        when(runtimePort.verifyCompletedOperations(eq(current), any(), any(), eq("lr-1"), eq("original-operator"), any()))
                .thenReturn(Map.of("passed", true, "verificationId", "lv-1"));
        when(pointerRepository.compareAndSetLandingResult(
                eq(current.pointer()), any(ChangePackageLandingCompletion.class))).thenReturn(true);

        Map<String, Object> outcome = manager.recoverLateSuccessfulCompletions(
                50, "late-completion-recovery");

        assertEquals(1, outcome.get("scanned"));
        assertEquals(1, outcome.get("reconciled"));
        assertEquals(0, outcome.get("unresolved"));
        assertEquals(0, outcome.get("failed"));
        verify(toolExecutionLedger).hasUnresolvedSideEffect("project-1", "lr-1");
        verify(runPort).complete(eq("lr-1"), eq(ChangePackageLandingRunStatus.SUCCEEDED), any());
        verify(pointerRepository).compareAndSetLandingResult(eq(current.pointer()),
                org.mockito.ArgumentMatchers.argThat(completion ->
                        completion.status() == ChangePackageStatus.LANDED
                                && completion.failureSummary().isEmpty()));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
        verify(runPort, never()).start(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void successfulWriteCannotBypassFailedIndependentPostconditions() {
        ChangePackageCurrent current = stubRecoveryVerification();
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("passed", false, "verificationId", "lv-failed"));
        when(pointerRepository.compareAndSetLandingResult(any(), any())).thenReturn(true);
        assertTrue(manager.reconcile("lr-1", "recovery"));
        verify(runPort).complete(eq("lr-1"), eq(ChangePackageLandingRunStatus.FAILED),
                org.mockito.ArgumentMatchers.argThat(value -> value instanceof Map<?, ?> m
                    && "LANDING_INDEPENDENT_VERIFICATION_FAILED".equals(m.get("reasonCode"))
                    && m.containsKey("independentVerification")));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unavailableOrUnpersistedVerificationCannotPromoteSuccessfulWrite() {
        stubRecoveryVerification();
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("proof store unavailable"));
        assertFalse(manager.reconcile("lr-1", "recovery"));
        verify(runPort, never()).complete(any(), any(), any());
        verify(pointerRepository, never()).compareAndSetLandingResult(any(), any());
    }

    @Test
    void emptyVerificationCannotPromoteSuccessfulWrite() {
        stubRecoveryVerification();
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), any(), any(), any())).thenReturn(Map.of());
        assertFalse(manager.reconcile("lr-1", "recovery"));
        verify(runPort, never()).complete(any(), any(), any());
    }

    @Test
    void terminalPackageCanBeObservedWithoutStartingAnotherLanding() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDED);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(runPort.find("lr-1")).thenReturn(Optional.of(recoveryRun()));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version()));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(fact("COMPLETED", "SUCCEEDED")));
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), eq("lr-1"), eq("alice"), any()))
                .thenReturn(Map.of("passed", true, "verificationId", "lv-manual"));
        assertEquals(true, manager.verifyLanding("cp-1", "alice").get("passed"));
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
        verify(runPort, never()).complete(any(), any(), any());
        verify(pointerRepository, never()).compareAndSetLandingResult(any(), any());
        verify(eventRepository).append(any(ChangePackageEvent.class));
    }

    private ChangePackageCurrent stubRecoveryVerification() {
        ChangePackageCurrent current = current(ChangePackageStatus.LANDING_FAILED);
        when(runPort.find("lr-1")).thenReturn(Optional.of(recoveryRun()));
        when(journalPort.operationFacts("lr-1")).thenReturn(List.of(fact("COMPLETED", "SUCCEEDED")));
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version()));
        when(runPort.executionActor("lr-1")).thenReturn("original-operator");
        return current;
    }

    @Test
    void explicitPassingReobservationReconcilesFailedRunUnderOriginalOwnerWithoutWriteReplay() {
        ChangePackageCurrent before = stubFailedManualVerification();
        ChangePackageCurrent after = current(ChangePackageStatus.LANDED);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(before), Optional.of(before),
                Optional.of(before), Optional.of(after));
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), eq("lr-1"), anyString(), any()))
                .thenReturn(Map.of("passed", true, "verificationId", "new-observation"));
        when(pointerRepository.compareAndSetLandingResult(any(), any())).thenReturn(true);

        Map<String, Object> proof = manager.verifyLanding("cp-1", "alice");

        assertEquals(true, proof.get("reconciled"));
        assertEquals("LANDED", proof.get("landingStatus"));
        verify(runtimePort).verifyCompletedOperations(any(), any(), any(), eq("lr-1"), eq("alice"), any());
        verify(runtimePort).verifyCompletedOperations(any(), any(), any(), eq("lr-1"), eq("original-operator"), any());
        verify(runPort).complete(eq("lr-1"), eq(ChangePackageLandingRunStatus.SUCCEEDED), any());
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void failedExplicitObservationDoesNotReconcile() {
        stubFailedManualVerification();
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(Map.of("passed", false));
        assertEquals(false, manager.verifyLanding("cp-1", "alice").get("passed"));
        verify(runPort, never()).complete(any(), any(), any());
        verify(pointerRepository, never()).compareAndSetLandingResult(any(), any());
    }

    @Test
    void incompleteOrUnknownFactsNeverPromoteEvenIfObservationClaimsPass() {
        for (LandingOperationFact fact : List.of(fact("UNKNOWN", "FAILED"), fact("NONE", "PENDING"))) {
            stubFailedManualVerification();
            when(journalPort.operationFacts("lr-1")).thenReturn(List.of(fact));
            when(runtimePort.verifyCompletedOperations(any(), any(), any(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("passed", true));
            manager.verifyLanding("cp-1", "alice");
        }
        verify(runPort, never()).complete(any(), any(), any());
        verify(pointerRepository, never()).compareAndSetLandingResult(any(), any());
    }

    @Test
    void secondIndependentFailureRemainsFailedAfterFirstObservationPasses() {
        stubFailedManualVerification();
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), eq("lr-1"), eq("alice"), any()))
                .thenReturn(Map.of("passed", true));
        when(runtimePort.verifyCompletedOperations(any(), any(), any(), eq("lr-1"), eq("original-operator"), any()))
                .thenReturn(Map.of("passed", false));
        when(pointerRepository.compareAndSetLandingResult(any(), any())).thenReturn(true);
        Map<String, Object> proof = manager.verifyLanding("cp-1", "alice");
        assertEquals(false, proof.get("reconciled"));
        assertEquals("LANDING_FAILED", proof.get("landingStatus"));
        verify(runPort).complete(eq("lr-1"), eq(ChangePackageLandingRunStatus.FAILED), any());
        verify(runtimePort, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void manualObservationWithUnresolvedSideEffectDoesNotReadOrClose() {
        stubFailedManualVerification();
        when(toolExecutionLedger.hasUnresolvedSideEffect("project-1", "lr-1")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> manager.verifyLanding("cp-1", "alice"));
        verify(runtimePort, never()).verifyCompletedOperations(any(), any(), any(), any(), any(), any());
        verify(runPort, never()).complete(any(), any(), any());
    }

    @Test
    void manualObservationRejectsApprovedTupleDrift() {
        stubFailedManualVerification();
        when(runPort.find("lr-1")).thenReturn(Optional.of(new ChangePackageLandingRun(
                "lr-1", "cp-1", "project-1", 2, "other-hash", "key",
                ChangePackageLandingRunStatus.FAILED, false, Map.of())));
        assertThrows(IllegalStateException.class, () -> manager.verifyLanding("cp-1", "alice"));
        verify(runPort, never()).complete(any(), any(), any());
    }

    @Test
    void manualObservationRejectsDifferentRunEvenWithSameApprovedTuple() {
        stubFailedManualVerification();
        when(runPort.find("lr-1")).thenReturn(Optional.of(new ChangePackageLandingRun(
                "lr-old", "cp-1", "project-1", 1, "hash-1", "key",
                ChangePackageLandingRunStatus.FAILED, false, Map.of())));
        assertThrows(IllegalStateException.class, () -> manager.verifyLanding("cp-1", "alice"));
        verify(runPort, never()).complete(any(), any(), any());
    }

    private ChangePackageCurrent stubFailedManualVerification() {
        ChangePackageCurrent current = stubRecoveryVerification();
        when(runPort.find("lr-1")).thenReturn(Optional.of(new ChangePackageLandingRun(
                "lr-1", "cp-1", "project-1", 1, "hash-1", "key",
                ChangePackageLandingRunStatus.FAILED, false, Map.of("reasonCode", "LANDING_INDEPENDENT_VERIFICATION_FAILED"))));
        return current;
    }

    private LandingOperationFact fact(String factStatus, String executionStatus) {
        Map<String, Object> payload = Map.of(
                "fact_status", factStatus,
                "status", executionStatus);
        return new LandingOperationFact(
                "op-1",
                LandingOperationFact.FactStatus.from(factStatus),
                LandingOperationFact.ExecutionStatus.from(executionStatus),
                "",
                "",
                "",
                payload);
    }

    private ChangePackageCommands.Land landCommand() {
        return new ChangePackageCommands.Land("cp-1",
                Map.of("version", 1, "packageHash", "hash-1"), "alice");
    }

    private ChangePackageCommands.Land customLandCommand(String idempotencyKey) {
        return new ChangePackageCommands.Land("cp-1",
                Map.of("version", 1, "packageHash", "hash-1",
                        "idempotencyKey", idempotencyKey), "alice");
    }

    private ChangePackageCurrent stubApproved() {
        ChangePackageCurrent current = current(ChangePackageStatus.APPROVED);
        ChangePackageVersion version = version();
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version));
        return current;
    }

    private ChangePackageCurrent current(ChangePackageStatus status) {
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1", status, 1, "hash-1", 1, "hash-1");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("riskLevel", "MEDIUM");
        state.put("validationAssessment", "ACCEPTABLE");
        state.put("evidenceJson", "{}");
        return new ChangePackageCurrent(
                1L, pointer, "session-1", "incident-1", "project-1",
                "agent-1", 1, ChangePackageType.MCP_OPERATION_PACKAGE,
                ChangePackageCurrentState.fromSnapshot(state),
                new ChangePackageSnapshot(snapshot(), "hash-1"), "lr-1", "creator", "approver",
                null, null, null);
    }

    private ChangePackageVersion version() {
        return new ChangePackageVersion(
                1L, "cp-1", 1, "hash-1", "APPROVED",
                new ChangePackageSnapshot(snapshot(), "hash-1"), "approved", "approver", null);
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("operationHash", "op-hash-1");
        operation.put("adapterType", "MCP");
        operation.put("toolsetId", "mcp-1");
        operation.put("toolName", "apply_config");
        operation.put("resourceScope", "config:x");
        operation.put("effectType", "MUTATE_TARGET_RESOURCE");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("packageId", "cp-1");
        snapshot.put("projectId", "project-1");
        snapshot.put("version", 1);
        snapshot.put("packageHash", "hash-1");
        snapshot.put("mcpSteps", List.of(operation));
        return snapshot;
    }

    private ChangePackageLandingRun recoveryRun() {
        return new ChangePackageLandingRun(
                "lr-1", "cp-1", "project-1", 1, "hash-1",
                "landing:cp-1:1:hash-1", ChangePackageLandingRunStatus.RUNNING, true, Map.of());
    }
}
