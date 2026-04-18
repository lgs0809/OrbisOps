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
    void landedRequiresIndependentVerificationAndUsesProdFullRuntime() {
        Fixture fixture = fixture();
        when(fixture.runtime.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content("LANDED\nverification passed").build());
        when(fixture.journal.operationFacts("lr-1")).thenReturn(List.of(succeededFact()));

        Map<String, Object> result = fixture.coordinator.execute(fixture.command);

        assertEquals("LANDED", result.get("status"));
        verify(fixture.verifier).verify(fixture.command, List.of(succeededFact()));
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
        assertTrue(instruction.contains("landingAuthorization"));
        assertTrue(instruction.contains("\"executionRequested\":true"));
        assertTrue(instruction.contains("platform-injected idempotency key"));
        assertFalse(instruction.contains("\"executionNotRequested\":true"));
        assertFalse(instruction.contains("\"arguments\":{\"projectId\":\"project-1\""));
        assertFalse(instruction.contains("\"actor\":\"prepare-agent\""));
        assertFalse(instruction.contains("\"executionKey\":\"prepare-time-key\""));
        assertFalse(instruction.contains("\"project_id\":\"project-1\""));
        assertFalse(instruction.contains("\"execution_key\":\"snake-case-prepare-key\""));
        assertFalse(instruction.contains("\"deadline\":\"2026-12-31T23:59:59Z\""));
        assertTrue(instruction.contains("business-data-must-remain"));
        assertTrue(instruction.contains("preferredPlan"));
        assertTrue(instruction.contains("rollbackPlan"));
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

    @Test
    void independentFailureOverridesModelSuccess() {
        Fixture f = fixture();
        when(f.runtime.execute(any())).thenReturn(OpsAgentChatResponse.builder().content("LANDED").build());
        when(f.verifier.verify(any(), any())).thenReturn(Map.of("passed", false));
        assertEquals("LANDING_INDEPENDENT_VERIFICATION_FAILED", f.coordinator.execute(f.command).get("reasonCode"));
    }

    @Test
    void verificationStorageFailurePreventsSuccess() {
        Fixture f = fixture();
        when(f.runtime.execute(any())).thenReturn(OpsAgentChatResponse.builder().content("LANDED").build());
        when(f.verifier.verify(any(), any())).thenThrow(new IllegalStateException("store unavailable"));
        assertEquals("LANDING_VERIFICATION_NOT_RECORDED", f.coordinator.execute(f.command).get("reasonCode"));
    }

    @Test
    void independentReadUsesFrozenContractAndPersistsProof() {
        Fixture f = fixture();
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        when(tools.executeReadOnly(any(), any())).thenReturn(readResponse("order-service", 7));
        Map<String, Object> proof = new OpsLandingIndependentVerifier(tools, records)
                .verify(f.command, List.of(succeededFact()));
        assertEquals(true, proof.get("passed"));
        verify(tools).executeReadOnly(argThat(r -> "project-1".equals(r.get("projectId"))
                && "read_deployment".equals(r.get("toolName"))
                && Map.of("service", "order-service").equals(r.get("arguments"))
                && "PRE_APPROVAL_WORKFLOW".equals(r.get("executionScope"))), org.mockito.ArgumentMatchers.eq("alice"));
        verify(records).record(any(), org.mockito.ArgumentMatchers.eq("lr-1"),
                org.mockito.ArgumentMatchers.eq("project-1"), org.mockito.ArgumentMatchers.eq("cp-1"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("hash-1"),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(proof));
    }

    @Test
    void recoveryVerifierUsesOriginalOwnerAndOnlyReadAuthority() {
        Fixture f = fixture();
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        when(tools.executeReadOnly(any(), any())).thenReturn(readResponse("order-service", 7));
        var proof = new OpsLandingIndependentVerifier(tools, records).verifyCompleted(
                "lr-1", f.command.approvedPlan(), "original-owner", List.of(succeededFact()));
        assertEquals(true, proof.get("passed"));
        verify(tools).executeReadOnly(argThat(r -> "PRE_APPROVAL_WORKFLOW".equals(r.get("executionScope"))
                && !r.containsKey("landingApproved")), org.mockito.ArgumentMatchers.eq("original-owner"));
        verify(records).record(any(), org.mockito.ArgumentMatchers.eq("lr-1"), any(), any(),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("hash-1"),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(proof));
    }

    @Test
    void modelClaimWithoutExecutionReceiptCannotEvenStartPostcheck() {
        Fixture f = fixture();
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        Map<String, Object> proof = new OpsLandingIndependentVerifier(tools, records).verify(f.command, List.of());
        assertEquals(false, proof.get("passed"));
        verify(tools, never()).executeReadOnly(any(), any());
    }

    @Test
    void wrongResourceOrWrongStateDoesNotPassEvenWithValidReceiptEnvelope() {
        Fixture f = fixture();
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        var verifier = new OpsLandingIndependentVerifier(tools, records);
        when(tools.executeReadOnly(any(), any())).thenReturn(readResponse("other-service", 7));
        assertEquals(false, verifier.verify(f.command, List.of(succeededFact())).get("passed"));
        when(tools.executeReadOnly(any(), any())).thenReturn(readResponse("order-service", 6));
        assertEquals(false, verifier.verify(f.command, List.of(succeededFact())).get("passed"));
    }

    @Test
    void blockedReadAndInvalidEnvelopeNeverPass() {
        Fixture f = fixture();
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        var verifier = new OpsLandingIndependentVerifier(tools, records);
        when(tools.executeReadOnly(any(), any())).thenReturn(Map.of("allowed", false, "reasonCode", "READ_ONLY_REQUIRED"));
        assertEquals(false, verifier.verify(f.command, List.of(succeededFact())).get("passed"));
        var invalid = new java.util.LinkedHashMap<>(readResponse("order-service", 7));
        invalid.remove("providerOutputHash");
        when(tools.executeReadOnly(any(), any())).thenReturn(invalid);
        assertEquals(false, verifier.verify(f.command, List.of(succeededFact())).get("passed"));
    }

    @Test
    void allIndependentChecksMustPassAndHaveDistinctReadKeys() {
        var second = Map.<String, Object>of("toolsetId", "mcp.prod-k8s", "toolName", "read_deployment",
                "arguments", Map.of("service", "order-service"),
                "expectedValues", Map.of("resourceKey", "order-service", "version", 7));
        var contract = new java.util.LinkedHashMap<>(second);
        contract.put("additionalChecks", List.of(second));
        Fixture f = fixture(contract);
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        var keys = new java.util.HashSet<String>();
        when(tools.executeReadOnly(any(), any())).thenAnswer(call -> {
            Map<String, Object> request = call.getArgument(0);
            keys.add(String.valueOf(request.get("idempotencyKey")));
            return readResponse("order-service", 7);
        });
        var verifier = new OpsLandingIndependentVerifier(tools, records);
        assertEquals(true, verifier.verify(f.command, List.of(succeededFact())).get("passed"));
        assertEquals(2, keys.size());
        org.mockito.Mockito.doReturn(readResponse("order-service", 7), readResponse("order-service", 6))
                .when(tools).executeReadOnly(any(), any());
        assertEquals(false, verifier.verify(f.command, List.of(succeededFact())).get("passed"));
    }

    @Test
    void malformedAdditionalCheckCannotBeIgnored() {
        var contract = new java.util.LinkedHashMap<String, Object>();
        contract.put("additionalChecks", List.of("invalid"));
        Fixture f = fixture(contract);
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        assertEquals(false, new OpsLandingIndependentVerifier(tools, records)
                .verify(f.command, List.of(succeededFact())).get("passed"));
        org.mockito.Mockito.verifyNoInteractions(tools);
    }

    @Test
    void postcheckTraversesArrayElementsAndCannotIgnoreOneFailedRequest() {
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        var expected = new java.util.LinkedHashMap<String, Object>();
        expected.put("resourceKey", "order-service");
        for (int i = 0; i < 20; i++) expected.put("requests." + i + ".httpStatus", 200);
        var contract = Map.<String, Object>of("toolsetId", "mcp.prod-k8s", "toolName", "read_deployment",
                "arguments", Map.of("service", "order-service"), "expectedValues", expected);
        var responses = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < 20; i++) responses.add(Map.of("httpStatus", 200));
        var response = new java.util.LinkedHashMap<>(readResponse("order-service", 7));
        response.put("normalizedContent", Map.of("resourceKey", "order-service", "requests", responses));
        when(tools.executeReadOnly(any(), any())).thenReturn(response);
        var verifier = new OpsLandingIndependentVerifier(tools, records);
        assertEquals(true, verifier.verify(fixture(contract).command, List.of(succeededFact())).get("passed"));
        responses.set(17, Map.of("httpStatus", 503));
        assertEquals(false, verifier.verify(fixture(contract).command, List.of(succeededFact())).get("passed"));
    }

    @Test
    void missingMalformedAndOutOfBoundsArrayFieldsFailClosed() {
        var tools = mock(cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService.class);
        var records = mock(cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort.class);
        var response = new java.util.LinkedHashMap<>(readResponse("order-service", 7));
        response.put("normalizedContent", Map.of("resourceKey", "order-service", "requests", List.of(Map.of("httpStatus", 200))));
        when(tools.executeReadOnly(any(), any())).thenReturn(response);
        var verifier = new OpsLandingIndependentVerifier(tools, records);
        for (String path : List.of("requests.1.httpStatus", "requests.-1.httpStatus", "requests.00.httpStatus",
                "requests.999999999999.httpStatus", "requests.*.httpStatus", "requests..httpStatus", "requests.0.absent")) {
            var contract = Map.<String, Object>of("toolsetId", "mcp.prod-k8s", "toolName", "read_deployment",
                    "arguments", Map.of("service", "order-service"),
                    "expectedValues", Map.of("resourceKey", "order-service", path, 200));
            assertEquals(false, verifier.verify(fixture(contract).command, List.of(succeededFact())).get("passed"), path);
        }
    }

    private Map<String, Object> readResponse(String resource, int version) {
        return Map.ofEntries(
                Map.entry("allowed", true), Map.entry("decision", "ALLOWED"),
                Map.entry("providerId", "prod-k8s"), Map.entry("remoteToolName", "read_deployment"),
                Map.entry("normalizedContent", Map.of("resourceKey", resource, "version", version)),
                Map.entry("mcpEnvelope", Map.of("orbisopsResultVersion", 1, "isError", false)),
                Map.entry("resultId", "tool-result-read"), Map.entry("fullOutputRef", "db:tool-result-read"),
                Map.entry("providerResultId", "tool-result-provider"),
                Map.entry("providerFullOutputRef", "db:tool-result-provider"),
                Map.entry("outputHash", "b".repeat(64)), Map.entry("providerOutputHash", "c".repeat(64)));
    }

    private Fixture fixture() { return fixture(null); }

    private Fixture fixture(Map<String, Object> postCheckOverride) {
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
                        Map.entry("arguments", Map.ofEntries(
                                Map.entry("service", "order-service"),
                                Map.entry("expectedVersion", 7),
                                Map.entry("projectId", "project-1"),
                                Map.entry("actor", "prepare-agent"),
                                Map.entry("executionKey", "prepare-time-key"),
                                Map.entry("project_id", "project-1"),
                                Map.entry("execution_key", "snake-case-prepare-key"),
                                Map.entry("configuration", Map.of("executionKey", "business-data-must-remain")),
                                Map.entry("deadline", "2026-12-31T23:59:59Z"),
                                Map.entry("executionNotRequested", true),
                                Map.entry("requiresHumanApproval", true),
                                Map.entry("permissionGranted", false))),
                        Map.entry("postCheck", postCheckOverride != null ? postCheckOverride : Map.of("toolsetId", "mcp.prod-k8s", "toolName", "read_deployment",
                                "arguments", Map.of("service", "order-service"),
                                "expectedValues", Map.of("resourceKey", "order-service", "version", 7))),
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
                Map.entry("preferredPlan", Map.of("steps", List.of(operation.raw()),
                        "rollbackPlan", Map.of("operation", operation.raw()))),
                Map.entry("approvalBoundaryJson", com.alibaba.fastjson.JSON.toJSONString(
                        Map.of("operations", List.of(operation.raw())))),
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
        OpsLandingIndependentVerifier verifier = mock(OpsLandingIndependentVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(Map.of("passed", true));
        return new Fixture(
                new OpsApprovedLandingAgentRunCoordinator(runtime, journal, toolLedger, verifier),
                runtime,
                journal,
                toolLedger,
                verifier,
                command);
    }

    private static LandingOperationFact succeededFact() {
        return new LandingOperationFact(
                "op-1",
                LandingOperationFact.FactStatus.COMPLETED,
                LandingOperationFact.ExecutionStatus.SUCCEEDED,
                "",
                "receipt-1",
                "a".repeat(64),
                Map.of("operationId", "op-1", "status", "SUCCEEDED"));
    }

    private record Fixture(
            OpsApprovedLandingAgentRunCoordinator coordinator,
            UnifiedAgentRuntime runtime,
            ChangePackageLandingJournalPort journal,
            ToolExecutionIdempotencyPort toolLedger,
            OpsLandingIndependentVerifier verifier,
            ApprovedLandingAgentCommand command) {
    }
}
