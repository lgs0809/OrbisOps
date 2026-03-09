package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingJournalPort;
import cn.lgs.orbisops.application.changepackage.LandingOperationFact;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsApprovedLandingAgentRunCoordinatorTest {

    @Test
    void landedStatusComesFromPlatformReactAndUsesProdFullRuntime() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content("LANDED\nverification passed").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of(succeededFact()));

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("LANDED", result.get("status"));
        assertEquals("PLATFORM_REACT_PROD_FULL", result.get("landingRuntimeRole"));
        assertEquals(OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_ID, result.get("landingRuntimeId"));
        assertTrue(Boolean.TRUE.equals(result.get("executedProductionAction")));
        verify(fixture.runtime).execute(argThat(request ->
                "lr-1".equals(request.getRunId())
                        && "lr-1".equals(request.getSessionId())
                        && "session-1".equals(String.valueOf(
                                request.getMetadata().get("preparationSessionId")))));
    }

    @Test
    void landingInstructionUsesCompactFrozenTaskBookWithApprovedBusinessArguments() {
        Fixture fixture = fixture();

        String instruction = fixture.command.instruction();

        assertTrue(instruction.contains("approved-landing-task-book-v1"));
        assertTrue(instruction.contains("restart order service safely"));
        assertTrue(instruction.contains("order-service"));
        assertTrue(instruction.contains("expectedVersion"));
        assertTrue(instruction.contains("platform-injected idempotency key"));
        assertFalse(instruction.contains("SHOULD_NOT_BE_IN_LANDING_TASK_BOOK"));
        assertTrue(instruction.length() < 10000);
    }

    @Test
    void planMeaningChangeReturnsNeedsReplanInsteadOfInventingNewApprovalScope() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content(
                        "NEEDS_REPLAN\nThe approved k8s-only plan now requires a database migration.").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of());

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("NEEDS_REPLAN", result.get("status"));
        assertEquals("APPROVED_PLAN_NO_LONGER_SUFFICIENT", result.get("reasonCode"));
        assertFalse(Boolean.TRUE.equals(result.get("executedProductionAction")));
        verify(fixture.journal, never()).markAllUnexecutedBlocked(any(), any(), any());
    }

    @Test
    void validPlanButExecutionFailureReturnsLandingFailed() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content("LANDING_FAILED\nproduction api unavailable").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of());

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("LANDING_FAILED", result.get("status"));
        assertEquals("LANDING_EXECUTION_FAILED", result.get("reasonCode"));
    }

    @Test
    void narrativeMentionOfLandedCannotBePromotedToProductionSuccess() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content(
                        "verification failed, not LANDED; manual inspection required").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of());

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("LANDING_FAILED", result.get("status"));
        assertEquals("LANDING_FINAL_STATUS_INVALID", result.get("reasonCode"));
    }

    @Test
    void unifiedToolExecutionUncertaintyOverridesNarrativeLandedResult() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content("LANDED\nlooks good").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of());
        when(fixture.toolLedger.hasUnresolvedSideEffect("project-1", "lr-1")).thenReturn(true);

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("LANDING_FAILED", result.get("status"));
        assertEquals("LANDING_RECONCILIATION_REQUIRED", result.get("eventType"));
        assertEquals(true, result.get("reconciliationRequired"));
        assertEquals(true, result.get("toolExecutionUncertainty"));
    }

    @Test
    void unresolvedUnknownStillRequiresReconciliationBeforeContinuing() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content("LANDED").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of(new LandingOperationFact(
                "op-1",
                LandingOperationFact.FactStatus.UNKNOWN,
                LandingOperationFact.ExecutionStatus.UNKNOWN,
                "MCP_EXECUTION_RESULT_UNKNOWN",
                "",
                "",
                Map.of())));

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("LANDING_FAILED", result.get("status"));
        assertEquals("LANDING_RECONCILIATION_REQUIRED", result.get("eventType"));
        assertEquals(true, result.get("reconciliationRequired"));
        assertEquals(true, result.get("manualInterventionRequired"));
    }

    private Fixture fixture() {
        OpsAgentDefinition preparationDefinition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(1)
                .definitionHash("definition-hash")
                .instruction("prepare")
                .modelId("model-1")
                .build();
        ChangePackageLandingOperation operation = new ChangePackageLandingOperation(
                "op-1", "op-hash", "MCP", "mcp.prod-k8s", "update_deployment",
                "order-service", "WRITE", Map.ofEntries(
                        Map.entry("targetEnvironment", "prod"),
                        Map.entry("mcpId", "prod-k8s"),
                        Map.entry("arguments", Map.of("service", "order-service", "expectedVersion", 7)),
                        Map.entry("verification", Map.of("successCriteria", "service healthy"))));
        ChangePackageSnapshot snapshot = new ChangePackageSnapshot(Map.ofEntries(
                Map.entry("packageId", "cp-1"),
                Map.entry("projectId", "project-1"),
                Map.entry("version", 1),
                Map.entry("packageHash", "hash-1"),
                Map.entry("packageType", ChangePackageType.MCP_OPERATION_PACKAGE.name()),
                Map.entry("riskLevel", "MEDIUM"),
                Map.entry("targetEnvironment", "prod"),
                Map.entry("objective", "restart order service safely"),
                Map.entry("unrelatedHugeEvidence", "SHOULD_NOT_BE_IN_LANDING_TASK_BOOK".repeat(200)),
                Map.entry("preparationAgentSnapshot", new OpsAgentRunExecutionContextCodec().encodeAgent(
                        new OpsAgentSnapshotFactory().fromDefinition(preparationDefinition)))), "hash-1");
        ChangePackageCurrent current = new ChangePackageCurrent(
                1,
                new ChangePackagePointer("cp-1", ChangePackageStatus.APPROVED, 1, "hash-1", 1, "hash-1"),
                "session-1", "incident-1", "project-1", "agent-1", 1,
                ChangePackageType.MCP_OPERATION_PACKAGE,
                ChangePackageCurrentState.fromSnapshot(snapshot.toMap()),
                snapshot, "", "creator", "approver",
                Instant.now(), Instant.now(), Instant.now());
        ChangePackageVersion version = new ChangePackageVersion(
                1, "cp-1", 1, "hash-1", ChangePackageStatus.APPROVED.name(),
                snapshot, "approved", "creator", LocalDateTime.now());
        ChangePackageLandingPlan plan = new ChangePackageLandingPlan(
                "cp-1", "project-1", 1, "hash-1", snapshot.toMap(), List.of(operation));
        ChangePackageLandingRequest request = new ChangePackageLandingRequest(
                1, "hash-1", "", Map.of("version", 1, "packageHash", "hash-1"));
        ApprovedPackageSnapshot approved = new ApprovedPackageSnapshot(
                "cp-1", 1, "hash-1", "project-1", "prod", "",
                Instant.now().plusSeconds(300));
        ApprovedLandingAgentCommand command = new ApprovedLandingAgentCommand(
                "alice", "lr-1", current, version, plan, request, approved);

        UnifiedAgentRuntime runtime = mock(UnifiedAgentRuntime.class);
        ChangePackageLandingJournalPort journal = mock(ChangePackageLandingJournalPort.class);
        ToolExecutionIdempotencyPort toolLedger = mock(ToolExecutionIdempotencyPort.class);
        when(journal.available()).thenReturn(true);
        return new Fixture(
                new OpsApprovedLandingAgentRunCoordinator(runtime, journal, toolLedger),
                runtime,
                journal,
                toolLedger,
                command);
    }

    private static LandingOperationFact succeededFact() {
        return new LandingOperationFact(
                "op-1",
                LandingOperationFact.FactStatus.COMPLETED,
                LandingOperationFact.ExecutionStatus.SUCCEEDED,
                "",
                "receipt-1",
                "hash",
                Map.of("operationId", "op-1", "status", "SUCCEEDED"));
    }

    private record Fixture(
            OpsApprovedLandingAgentRunCoordinator coordinator,
            UnifiedAgentRuntime runtime,
            ChangePackageLandingJournalPort journal,
            ToolExecutionIdempotencyPort toolLedger,
            ApprovedLandingAgentCommand command) {
    }
}
