package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageApprovalRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApproval;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangePackageApprovalUseCaseTest {

    private IChangePackageCurrentRepository currentRepository;
    private IChangePackageVersionRepository versionRepository;
    private IChangePackagePointerRepository pointerRepository;
    private IChangePackageApprovalRepository approvalRepository;
    private IChangePackageEventRepository eventRepository;
    private ChangePackageApprovalProofPort proofPort;
    private ChangePackageAuditPort auditPort;
    private ChangePackageSkillSignalPort skillSignalPort;
    private ChangePackageApprovalUseCase useCase;

    @BeforeEach
    void setUp() {
        currentRepository = mock(IChangePackageCurrentRepository.class);
        versionRepository = mock(IChangePackageVersionRepository.class);
        pointerRepository = mock(IChangePackagePointerRepository.class);
        approvalRepository = mock(IChangePackageApprovalRepository.class);
        eventRepository = mock(IChangePackageEventRepository.class);
        proofPort = mock(ChangePackageApprovalProofPort.class);
        auditPort = mock(ChangePackageAuditPort.class);
        skillSignalPort = mock(ChangePackageSkillSignalPort.class);
        when(currentRepository.available()).thenReturn(true);
        when(versionRepository.available()).thenReturn(true);
        when(pointerRepository.available()).thenReturn(true);
        when(approvalRepository.available()).thenReturn(true);
        when(eventRepository.available()).thenReturn(true);
        useCase = new ChangePackageApprovalUseCase(
                currentRepository,
                versionRepository,
                pointerRepository,
                approvalRepository,
                eventRepository,
                proofPort,
                auditPort,
                skillSignalPort,
                new DirectTransactionPort());
    }

    @Test
    void rejectsApprovalForNonCurrentVersion() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 2, "hash-2",
                "MEDIUM", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.approve(approve(1, "hash-1", "approver", Map.of())));

        assertTrue(error.getMessage().contains("VERSION_MISMATCH"));
        verify(versionRepository, never()).find(any(), anyInt());
        verify(approvalRepository, never()).saveDecision(any());
    }

    @Test
    void rejectsApprovalWhenPackageHashDoesNotMatchCurrentPointer() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "MEDIUM", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));

        assertThrows(IllegalStateException.class,
                () -> useCase.approve(approve(1, "stale", "approver", Map.of())));

        verify(versionRepository, never()).find(any(), anyInt());
        verify(approvalRepository, never()).saveDecision(any());
    }

    @Test
    void rejectsApprovalWhenValidationHasNotPassed() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "MEDIUM", "REJECTED", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        ChangePackageVersion version = version("MEDIUM", "REJECTED", false);
        stubCurrentAndVersion(current, version);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.approve(approve(1, "hash-1", "approver", Map.of())));

        assertTrue(error.getMessage().contains("VALIDATION"));
        verify(approvalRepository, never()).saveDecision(any());
        verify(pointerRepository, never()).compareAndSetApproved(any(), any(), any());
    }

    @Test
    void rejectsSelfApprovalBySeparationOfDutiesPolicy() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "MEDIUM", "ACCEPTABLE", "alice", ChangePackageType.MCP_OPERATION_PACKAGE);
        stubCurrentAndVersion(current, version("MEDIUM", "ACCEPTABLE", false));

        SecurityException error = assertThrows(SecurityException.class,
                () -> useCase.approve(approve(1, "hash-1", "alice", Map.of())));

        assertTrue(error.getMessage().contains("SELF_APPROVAL"));
        verify(approvalRepository, never()).saveDecision(any());
    }

    @Test
    void ledgerWriteFailureDoesNotAdvancePointer() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "HIGH", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        stubCurrentAndVersion(current, version("HIGH", "ACCEPTABLE", false));
        doThrow(new IllegalStateException("CHANGE_PACKAGE_APPROVAL_LEDGER_WRITE_FAILED"))
                .when(approvalRepository).saveDecision(any(ChangePackageApproval.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.approve(approve(1, "hash-1", "approver", Map.of())));

        assertTrue(error.getMessage().contains("LEDGER_WRITE_FAILED"));
        verify(pointerRepository, never()).compareAndSetApproved(any(), any(), any());
        verify(eventRepository, never()).append(any());
    }

    @Test
    void highRiskApprovalCannotAdvanceUntilDistinctCountReachesTwo() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "HIGH", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        ChangePackageVersion version = version("HIGH", "ACCEPTABLE", false);
        stubCurrentAndVersion(current, version);
        when(approvalRepository.countDistinctApproved("cp-1", 1, "hash-1")).thenReturn(1);

        ChangePackageApprovalOutcome outcome = useCase.approve(
                approve(1, "hash-1", "approver-1", Map.of()));

        assertEquals(ChangePackageStatus.REVIEWING, outcome.status());
        assertEquals(2, outcome.requiredApprovals());
        assertEquals(1, outcome.approvedCount());
        verify(pointerRepository, never()).compareAndSetApproved(any(), any(), any());
        verify(eventRepository).append(any(ChangePackageEvent.class));
        verify(auditPort).record(eq("project-1"), eq("approval-recorded"), eq("cp-1"), eq(current), any());
    }

    @Test
    void successfulApprovalFreezesVersionAndHashAndPreservesSideEffectOrder() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "HIGH", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        ChangePackageVersion version = version("HIGH", "ACCEPTABLE", false);
        stubCurrentAndVersion(current, version);
        when(approvalRepository.countDistinctApproved("cp-1", 1, "hash-1")).thenReturn(2);
        when(pointerRepository.compareAndSetApproved(current.pointer(), version.snapshot(), "approver-2"))
                .thenReturn(true);

        ChangePackageApprovalOutcome outcome = useCase.approve(
                approve(1, "hash-1", "approver-2", Map.of()));

        assertEquals(ChangePackageStatus.APPROVED, outcome.status());
        assertEquals(1, outcome.version());
        assertEquals("hash-1", outcome.packageHash());
        InOrder order = inOrder(approvalRepository, pointerRepository, eventRepository, auditPort, skillSignalPort);
        order.verify(approvalRepository).saveDecision(any(ChangePackageApproval.class));
        order.verify(approvalRepository).countDistinctApproved("cp-1", 1, "hash-1");
        order.verify(pointerRepository).compareAndSetApproved(current.pointer(), version.snapshot(), "approver-2");
        order.verify(eventRepository).append(any(ChangePackageEvent.class));
        order.verify(auditPort).record(eq("project-1"), eq("approve"), eq("cp-1"), eq(current), any());
        order.verify(skillSignalPort).recordAccepted(current, version, "approver-2");
        order.verify(skillSignalPort).reconcileApprovedOutcome(version);
    }

    @Test
    void approvalCasConflictFailsAfterLedgerButBeforeEventAndSignals() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "MEDIUM", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        ChangePackageVersion version = version("MEDIUM", "ACCEPTABLE", false);
        stubCurrentAndVersion(current, version);
        when(approvalRepository.countDistinctApproved("cp-1", 1, "hash-1")).thenReturn(2);
        when(pointerRepository.compareAndSetApproved(current.pointer(), version.snapshot(), "approver"))
                .thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.approve(approve(1, "hash-1", "approver", Map.of())));

        assertTrue(error.getMessage().contains("CAS"));
        verify(approvalRepository).saveDecision(any(ChangePackageApproval.class));
        verify(eventRepository, never()).append(any());
        verify(auditPort, never()).record(any(), any(), any(), any(), any());
        verify(skillSignalPort, never()).recordAccepted(any(), any(), any());
    }

    @Test
    void criticalApprovalRequiresAdminConfirmationAndRollbackMaterial() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "CRITICAL", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        stubCurrentAndVersion(current, version("CRITICAL", "ACCEPTABLE", false));

        assertThrows(IllegalStateException.class,
                () -> useCase.approve(approve(1, "hash-1", "approver",
                        Map.of("actorScope", "admin", "adminConfirmation", true))));

        verify(approvalRepository, never()).saveDecision(any());
    }

    @Test
    void rejectUsesAggregateTransitionAndWritesCasEventThenAudit() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1",
                "MEDIUM", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(pointerRepository.compareAndSetStatus(current.pointer(), ChangePackageStatus.REJECTED)).thenReturn(true);

        ChangePackageApprovalOutcome outcome = useCase.reject(
                new ChangePackageCommands.Reject("cp-1", Map.of("reason", "needs revision"), "reviewer"));

        assertEquals(ChangePackageStatus.REJECTED, outcome.status());
        InOrder order = inOrder(pointerRepository, eventRepository, auditPort);
        order.verify(pointerRepository).compareAndSetStatus(current.pointer(), ChangePackageStatus.REJECTED);
        order.verify(eventRepository).append(any(ChangePackageEvent.class));
        order.verify(auditPort).record(eq("project-1"), eq("reject"), eq("cp-1"), eq(current), any());
    }

    @Test
    void rejectCannotBypassAggregateStateMachine() {
        ChangePackageCurrent current = current(ChangePackageStatus.READY_FOR_REVIEW, 1, "hash-1",
                "MEDIUM", "ACCEPTABLE", "creator", ChangePackageType.MCP_OPERATION_PACKAGE);
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));

        assertThrows(IllegalStateException.class,
                () -> useCase.reject(new ChangePackageCommands.Reject("cp-1", Map.of(), "reviewer")));

        verify(pointerRepository, never()).compareAndSetStatus(any(), any());
        verify(eventRepository, never()).append(any());
    }

    private void stubCurrentAndVersion(ChangePackageCurrent current, ChangePackageVersion version) {
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version));
    }

    private ChangePackageCommands.Approve approve(int version,
                                                  String packageHash,
                                                  String actor,
                                                  Map<String, Object> context) {
        return new ChangePackageCommands.Approve("cp-1", version, packageHash, actor, context);
    }

    private ChangePackageCurrent current(ChangePackageStatus status,
                                         int version,
                                         String packageHash,
                                         String riskLevel,
                                         String validationAssessment,
                                         String creator,
                                         ChangePackageType packageType) {
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1", status, version, packageHash, 0, "");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("riskLevel", riskLevel);
        state.put("validationAssessment", validationAssessment);
        return new ChangePackageCurrent(
                1L,
                pointer,
                "session-1",
                "incident-1",
                "project-1",
                "agent-1",
                1,
                packageType,
                ChangePackageCurrentState.fromSnapshot(state),
                null,
                "",
                creator,
                "",
                null,
                null,
                null);
    }

    private ChangePackageVersion version(String riskLevel,
                                         String validationAssessment,
                                         boolean rollbackMaterial) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("riskLevel", riskLevel);
        operation.put("effectType", "MUTATE_TARGET_RESOURCE");
        operation.put("effectScope", "PRODUCTION");
        operation.put("mutability", "PROD_MUTATING");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("packageId", "cp-1");
        snapshot.put("version", 1);
        snapshot.put("packageHash", "hash-1");
        snapshot.put("projectId", "project-1");
        snapshot.put("packageType", "MCP_OPERATION_PACKAGE");
        snapshot.put("riskLevel", riskLevel);
        snapshot.put("validationAssessment", validationAssessment);
        snapshot.put("mcpSteps", List.of(operation));
        if (rollbackMaterial) {
            snapshot.put("rollbackSteps", List.of(Map.of("summary", "restore")));
        }
        return new ChangePackageVersion(
                1L,
                "cp-1",
                1,
                "hash-1",
                "REVIEWING",
                new ChangePackageSnapshot(snapshot, "hash-1"),
                "test",
                "creator",
                null);
    }

    private static final class DirectTransactionPort implements ChangePackageTransactionPort {
        @Override
        public <T> T required(Supplier<T> action) {
            return action.get();
        }
    }
}
