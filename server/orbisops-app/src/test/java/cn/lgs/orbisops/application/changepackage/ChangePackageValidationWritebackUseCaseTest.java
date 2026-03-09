package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageEventRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackagePointerRepository;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageVersionRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationFailure;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationWriteback;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class ChangePackageValidationWritebackUseCaseTest {

    private IChangePackageCurrentRepository currentRepository;
    private IChangePackageVersionRepository versionRepository;
    private IChangePackagePointerRepository pointerRepository;
    private IChangePackageEventRepository eventRepository;
    private ChangePackageValidationProofPort proofPort;
    private ChangePackageAuditPort auditPort;
    private ChangePackageValidationWritebackUseCase useCase;

    @BeforeEach
    void setUp() {
        currentRepository = mock(IChangePackageCurrentRepository.class);
        versionRepository = mock(IChangePackageVersionRepository.class);
        pointerRepository = mock(IChangePackagePointerRepository.class);
        eventRepository = mock(IChangePackageEventRepository.class);
        proofPort = mock(ChangePackageValidationProofPort.class);
        auditPort = mock(ChangePackageAuditPort.class);
        when(currentRepository.available()).thenReturn(true);
        when(versionRepository.available()).thenReturn(true);
        when(pointerRepository.available()).thenReturn(true);
        when(eventRepository.available()).thenReturn(true);
        useCase = new ChangePackageValidationWritebackUseCase(
                currentRepository,
                versionRepository,
                pointerRepository,
                eventRepository,
                proofPort,
                auditPort,
                new DirectTransactionPort());
    }

    @Test
    void passedWritebackRejectsUntrustedExecutionTokenBeforeProofLookup() {
        ChangePackageCurrent current = current(ChangePackageStatus.VALIDATING, 1, "hash-1");
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));

        SecurityException error = assertThrows(SecurityException.class,
                () -> useCase.writeBack(command(true, Map.of(
                        "_validationExecutionToken", "stale",
                        "reasonCode", "READY_FOR_REVIEW"))));

        assertTrue(error.getMessage().contains("VALIDATION_RESULT_UNTRUSTED"));
        verify(versionRepository, never()).find(any(), anyInt());
        verify(proofPort, never()).verifySourceProofs(any(), any(), any());
    }

    @Test
    void passedWritebackRejectsSourceVersionHashDrift() {
        ChangePackageCurrent current = current(ChangePackageStatus.VALIDATING, 1, "hash-1");
        ChangePackageVersion staleVersion = version(1, "hash-stale");
        stub(current, staleVersion);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.writeBack(command(true, passedReport(current))));

        assertTrue(error.getMessage().contains("SOURCE_HASH_MISMATCH"));
        verify(proofPort, never()).verifySourceProofs(any(), any(), any());
        verify(proofPort, never()).recordWritebackProof(any(), any(), any());
        verify(pointerRepository, never()).compareAndSetVersion(any(), any(), any());
    }

    @Test
    void trustedSourceProofFailureStopsBeforeCasAndVersionAppend() {
        ChangePackageCurrent current = current(ChangePackageStatus.VALIDATING, 1, "hash-1");
        ChangePackageVersion version = version(1, "hash-1");
        stub(current, version);
        doThrow(new IllegalStateException("VALIDATION_SOURCE_PROOF_UNTRUSTED"))
                .when(proofPort).verifySourceProofs(eq(current), eq(version), any());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.writeBack(command(true, passedReport(current))));

        assertTrue(error.getMessage().contains("PROOF_UNTRUSTED"));
        verify(pointerRepository, never()).compareAndSetVersion(any(), any(), any());
        verify(versionRepository, never()).append(any());
        verify(eventRepository, never()).append(any());
    }

    @Test
    void successfulPassedWritebackRecordsProofThenCasVersionEventAndAudit() {
        ChangePackageCurrent current = current(ChangePackageStatus.VALIDATING, 1, "hash-1");
        ChangePackageVersion sourceVersion = version(1, "hash-1");
        stub(current, sourceVersion);
        when(pointerRepository.compareAndSetVersion(eq(current.pointer()), any(), any())).thenReturn(true);

        ChangePackageValidationOutcome outcome = useCase.writeBack(command(true, passedReport(current)));

        assertEquals(ChangePackageStatus.READY_FOR_REVIEW, outcome.status());
        assertEquals(2, outcome.version());
        assertTrue(outcome.packageHash().length() >= 32);
        InOrder order = inOrder(proofPort, pointerRepository, versionRepository, eventRepository, auditPort);
        order.verify(proofPort).verifySourceProofs(eq(current), eq(sourceVersion), any());
        order.verify(proofPort).recordWritebackProof(eq(current), any(ChangePackageValidationWriteback.class), eq("validator"));
        order.verify(pointerRepository).compareAndSetVersion(eq(current.pointer()), any(), any());
        ArgumentCaptor<ChangePackageVersion> appendedVersion = ArgumentCaptor.forClass(ChangePackageVersion.class);
        order.verify(versionRepository).append(appendedVersion.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> preparationAgentSnapshot = (Map<String, Object>) appendedVersion.getValue()
                .snapshot().toMap().get("preparationAgentSnapshot");
        assertEquals("agent-1", preparationAgentSnapshot.get("agentId"));
        assertEquals(1, preparationAgentSnapshot.get("agentVersion"));
        assertEquals("definition-hash-1", preparationAgentSnapshot.get("definitionHash"));
        order.verify(eventRepository).append(any(ChangePackageEvent.class));
        order.verify(auditPort).record(eq("project-1"), eq("validation-passed"), eq("cp-1"), eq(current), any());
    }

    @Test
    void passedWritebackCasConflictDoesNotAppendVersionEventOrAudit() {
        ChangePackageCurrent current = current(ChangePackageStatus.VALIDATING, 1, "hash-1");
        ChangePackageVersion sourceVersion = version(1, "hash-1");
        stub(current, sourceVersion);
        when(pointerRepository.compareAndSetVersion(eq(current.pointer()), any(), any())).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.writeBack(command(true, passedReport(current))));

        assertTrue(error.getMessage().contains("CAS"));
        verify(proofPort).recordWritebackProof(eq(current), any(ChangePackageValidationWriteback.class), eq("validator"));
        verify(versionRepository, never()).append(any());
        verify(eventRepository, never()).append(any());
        verify(auditPort, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void failedValidationUsesAggregateCasThenEventAndAuditWithoutProof() {
        ChangePackageCurrent current = current(ChangePackageStatus.VALIDATING, 1, "hash-1");
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(pointerRepository.compareAndSetValidationFailure(
                eq(current.pointer()), any(ChangePackageValidationFailure.class))).thenReturn(true);

        ChangePackageValidationOutcome outcome = useCase.writeBack(command(false,
                Map.of("reasonCode", "DRY_RUN_FAILED")));

        assertEquals(ChangePackageStatus.VALIDATION_FAILED, outcome.status());
        assertEquals(1, outcome.version());
        InOrder order = inOrder(pointerRepository, eventRepository, auditPort);
        order.verify(pointerRepository).compareAndSetValidationFailure(
                eq(current.pointer()), org.mockito.ArgumentMatchers.argThat(failure ->
                        failure.status() == ChangePackageStatus.VALIDATION_FAILED
                                && "NEEDS_REFINEMENT".equals(failure.assessment())
                                && "DRY_RUN_FAILED".equals(failure.reasonCode())
                                && "DRY_RUN_FAILED".equals(failure.failureSummary().get("reasonCode"))));
        order.verify(eventRepository).append(any(ChangePackageEvent.class));
        order.verify(auditPort).record(eq("project-1"), eq("validation-failed"), eq("cp-1"), eq(current), any());
        verify(proofPort, never()).verifySourceProofs(any(), any(), any());
    }

    @Test
    void validationWritebackCannotBypassAggregateStateMachine() {
        ChangePackageCurrent current = current(ChangePackageStatus.REVIEWING, 1, "hash-1");
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));

        assertThrows(IllegalStateException.class,
                () -> useCase.writeBack(command(false, Map.of("reasonCode", "FAILED"))));

        verify(pointerRepository, never()).compareAndSetValidationFailure(
                any(), any(ChangePackageValidationFailure.class));
        verify(eventRepository, never()).append(any());
    }

    private ChangePackageCommands.ValidationWriteback command(boolean passed, Map<String, Object> report) {
        return new ChangePackageCommands.ValidationWriteback("cp-1", passed, report, "validator");
    }

    private Map<String, Object> passedReport(ChangePackageCurrent current) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reasonCode", "READY_FOR_REVIEW");
        report.put("trustedProofRefs", List.of(Map.of(
                "proofId", "proof-1",
                "proofType", "CONTROLLED_BASH_TEST")));
        report.put("testProofHash", "test-hash-1");
        report.put("_validationExecutionToken",
                useCase.executionToken(current.packageId(), current.version(), current.packageHash()));
        return report;
    }

    private void stub(ChangePackageCurrent current, ChangePackageVersion version) {
        when(currentRepository.find("cp-1")).thenReturn(Optional.of(current));
        when(versionRepository.find("cp-1", 1)).thenReturn(Optional.of(version));
    }

    private ChangePackageCurrent current(ChangePackageStatus status, int version, String packageHash) {
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1", status, version, packageHash, 0, "");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("riskLevel", "MEDIUM");
        state.put("validationAssessment", "");
        state.put("evidenceJson", "{}");
        return new ChangePackageCurrent(
                1L,
                pointer,
                "session-1",
                "incident-1",
                "project-1",
                "agent-1",
                1,
                ChangePackageType.MCP_OPERATION_PACKAGE,
                ChangePackageCurrentState.fromSnapshot(state),
                null,
                "",
                "creator",
                "",
                null,
                null,
                null);
    }

    private ChangePackageVersion version(int version, String packageHash) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("adapterType", "MCP");
        operation.put("mcpId", "mcp-1");
        operation.put("toolName", "apply_config");
        operation.put("arguments", Map.of("key", "x"));
        operation.put("resourceScope", "config:x");
        operation.put("targetEnvironment", "prod");
        operation.put("riskLevel", "MEDIUM");
        operation.put("effectType", "MUTATE_TARGET_RESOURCE");
        operation.put("effectScope", "PRODUCTION");
        operation.put("mutability", "PROD_MUTATING");
        operation.put("writesTargetResource", true);
        operation.put("requiresChangePackage", true);
        operation.put("requiresApproval", true);
        operation.put("readOnly", false);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("packageId", "cp-1");
        snapshot.put("sessionId", "session-1");
        snapshot.put("runId", "run-1");
        snapshot.put("incidentId", "incident-1");
        snapshot.put("projectId", "project-1");
        snapshot.put("preparationAgentId", "agent-1");
        snapshot.put("preparationAgentVersion", 1);
        snapshot.put("preparationAgentSnapshot", Map.of(
                "agentId", "agent-1",
                "agentVersion", 1,
                "definitionHash", "definition-hash-1"));
        snapshot.put("packageType", "MCP_OPERATION_PACKAGE");
        snapshot.put("status", "VALIDATING");
        snapshot.put("version", version);
        snapshot.put("packageHash", packageHash);
        snapshot.put("riskLevel", "MEDIUM");
        snapshot.put("mcpSteps", List.of(operation));
        snapshot.put("contextBundleId", "context-1");
        snapshot.put("contextBundleHash", "context-hash-1");
        snapshot.put("usedSkillRefsHash", "skill-refs-hash-1");
        snapshot.put("toolsetBoundaryHash", "toolset-boundary-hash-1");
        snapshot.put("runtimeBoundaryHash", "runtime-boundary-hash-1");
        return new ChangePackageVersion(
                1L,
                "cp-1",
                version,
                packageHash,
                "VALIDATING",
                new ChangePackageSnapshot(snapshot, packageHash),
                "source",
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
