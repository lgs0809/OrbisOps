package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.changepackage.LandingOperationPayload;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProgressiveMcpInvocationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsToolSideEffectRecoveryServiceTest {

    @Test
    void authoritativeReceiptResolvesLedgerProjectsJournalAndReconcilesLandingWithoutRedispatch() {
        Fixture fixture = fixture();
        ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect = sideEffect(context());
        Map<String, Object> receipt = receipt("execution-1", "project-1", "alice");
        when(fixture.toolExecution.unresolvedSideEffects(anyInt())).thenReturn(List.of(sideEffect));
        when(fixture.runtimeConfigs.resolve("project-1", "service-control"))
                .thenReturn(Optional.of(runtimeConfig()));
        when(fixture.mcp.invoke(any(), any())).thenReturn(JSON.toJSONString(Map.of(
                "content", List.of(Map.of("type", "text", "text", JSON.toJSONString(receipt))))));
        LandingOperationPayload payload = new LandingOperationPayload(Map.of(), "", "", "");
        when(fixture.landingJournal.payload(any())).thenReturn(payload);

        Map<String, Object> result = fixture.service.recoverOnce(10);

        assertEquals(1, result.get("scanned"));
        assertEquals(1, result.get("reconciled"));
        assertEquals(0, result.get("unresolved"));
        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OpsMcpServerConfig> config = ArgumentCaptor.forClass(OpsMcpServerConfig.class);
        verify(fixture.mcp).invoke(config.capture(), request.capture());
        Map<String, Object> query = JSON.parseObject(request.getValue());
        assertEquals("get_operation_receipt", query.get("toolName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) query.get("arguments");
        assertEquals("execution-1", arguments.get("executionKey"));
        assertEquals("alice", arguments.get("actor"));
        assertEquals(List.of("get_operation_receipt"), config.getValue().getAllowedTools());
        assertTrue(Boolean.TRUE.equals(config.getValue().getLandingApproved()));

        ArgumentCaptor<ToolExecutionIdempotencyPort.ResolveSideEffectCommand> resolution =
                ArgumentCaptor.forClass(ToolExecutionIdempotencyPort.ResolveSideEffectCommand.class);
        verify(fixture.toolExecution).resolveUncertainSideEffect(resolution.capture());
        assertEquals(ToolExecutionIdempotencyPort.SideEffectResolution.CONFIRMED_SUCCEEDED,
                resolution.getValue().resolution());
        assertEquals("receipt-1", resolution.getValue().evidenceId());
        assertEquals(String.valueOf(receipt.get("resultHash")).substring("sha256:".length()),
                resolution.getValue().evidenceHash());
        verify(fixture.landingJournal).completeFromToolExecution(
                eq("landing-run-1"), eq("restart-service"), eq("restart_service"),
                eq("execution-1"), eq(payload));
        verify(fixture.landingProcessManager).reconcile("landing-run-1", "tool-side-effect-recovery");
    }

    @Test
    void mismatchedOrTamperedReceiptMustRemainReviewRequired() {
        Fixture fixture = fixture();
        ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect = sideEffect(context());
        Map<String, Object> receipt = new LinkedHashMap<>(receipt("execution-other", "project-1", "alice"));
        when(fixture.toolExecution.unresolvedSideEffects(anyInt())).thenReturn(List.of(sideEffect));
        when(fixture.runtimeConfigs.resolve("project-1", "service-control"))
                .thenReturn(Optional.of(runtimeConfig()));
        when(fixture.mcp.invoke(any(), any())).thenReturn(JSON.toJSONString(receipt));

        Map<String, Object> result = fixture.service.recoverOnce(10);

        assertEquals(1, result.get("unresolved"));
        verify(fixture.toolExecution, never()).resolveUncertainSideEffect(any());
        verify(fixture.landingJournal, never()).completeFromToolExecution(
                any(), any(), any(), any(), any());
        verify(fixture.landingProcessManager, never()).reconcile(any(), any());
    }

    @Test
    void unsupportedSideEffectMustNotCallMcp() {
        Fixture fixture = fixture();
        Map<String, Object> unsupported = new LinkedHashMap<>(context());
        unsupported.put("remoteToolName", "arbitrary_write");
        when(fixture.toolExecution.unresolvedSideEffects(anyInt()))
                .thenReturn(List.of(sideEffect(unsupported)));

        Map<String, Object> result = fixture.service.recoverOnce(10);

        assertEquals(1, result.get("unsupported"));
        verify(fixture.runtimeConfigs, never()).resolve(any(), any());
        verify(fixture.mcp, never()).invoke(any(), any());
    }

    @Test
    void missingOrUnreviewedReceiptPolicyFailsClosed() {
        Fixture fixture = fixture();
        when(fixture.toolExecution.unresolvedSideEffects(anyInt()))
                .thenReturn(List.of(sideEffect(context())));
        when(fixture.runtimeConfigs.resolve("project-1", "service-control"))
                .thenReturn(Optional.of(runtimeConfig()));
        when(fixture.mcp.invoke(any(), any()))
                .thenThrow(new SecurityException("MCP_POLICY_MISSING"));

        Map<String, Object> result = fixture.service.recoverOnce(10);

        assertEquals(1, result.get("unresolved"));
        verify(fixture.toolExecution, never()).resolveUncertainSideEffect(any());
    }

    private Fixture fixture() {
        ToolExecutionApplicationService toolExecution = mock(ToolExecutionApplicationService.class);
        OpsProjectMcpRuntimeConfigService runtimeConfigs = mock(OpsProjectMcpRuntimeConfigService.class);
        OpsProgressiveMcpInvocationService mcp = mock(OpsProgressiveMcpInvocationService.class);
        LandingOperationJournalApplicationService landingJournal = mock(LandingOperationJournalApplicationService.class);
        ChangePackageLandingProcessManager landingProcessManager = mock(ChangePackageLandingProcessManager.class);
        return new Fixture(
                toolExecution,
                runtimeConfigs,
                mcp,
                landingJournal,
                landingProcessManager,
                new OpsToolSideEffectRecoveryService(
                        toolExecution, runtimeConfigs, mcp, landingJournal, landingProcessManager));
    }

    private ToolExecutionIdempotencyPort.UnresolvedSideEffect sideEffect(Map<String, Object> context) {
        return new ToolExecutionIdempotencyPort.UnresolvedSideEffect(
                "execution-1",
                "project-1",
                "landing-run-1",
                "node-1",
                "a".repeat(64),
                "b".repeat(64),
                context,
                "TOOL_EXECUTION_STALE_SIDE_EFFECT_REVIEW_REQUIRED",
                "reconcile",
                Instant.parse("2026-08-10T04:00:00Z"));
    }

    private Map<String, Object> context() {
        return Map.ofEntries(
                Map.entry("adapterType", "MCP"),
                Map.entry("toolsetId", "mcp.service-control"),
                Map.entry("toolName", "restart_service"),
                Map.entry("providerType", "MCP"),
                Map.entry("providerId", "service-control"),
                Map.entry("mcpServerId", "service-control"),
                Map.entry("remoteToolName", "restart_service"),
                Map.entry("actor", "alice"),
                Map.entry("executionScope", "APPROVED_LANDING"),
                Map.entry("changePackageId", "cp-1"),
                Map.entry("approvedPackageHash", "c".repeat(64)),
                Map.entry("approvedPackageVersion", "1"),
                Map.entry("operationId", "restart-service"));
    }

    private OpsMcpServerConfig runtimeConfig() {
        return OpsMcpServerConfig.builder()
                .name("service-control")
                .mcpId("service-control")
                .toolId("service-control")
                .projectId("project-1")
                .allowedTools(List.of("restart_service", "get_operation_receipt"))
                .blockedTools(List.of())
                .toolCapabilities(new LinkedHashMap<>(Map.of("resourceEnvironment", "production")))
                .build();
    }

    private Map<String, Object> receipt(String executionKey, String projectId, String actor) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("status", "SUCCEEDED");
        receipt.put("receiptId", "receipt-1");
        receipt.put("operation", "restart_service");
        receipt.put("executionKey", executionKey);
        receipt.put("projectId", projectId);
        receipt.put("service", "order-service");
        receipt.put("actor", actor);
        receipt.put("expectedVersion", 1);
        receipt.put("previousVersion", 1);
        receipt.put("currentVersion", 2);
        receipt.put("previousRestartCount", 0);
        receipt.put("currentRestartCount", 1);
        receipt.put("completedAt", "2026-08-10T04:00:01Z");
        receipt.put("operationInputHash", "d".repeat(64));
        receipt.put("hashVersion", 1);
        receipt.put("resultHash", "sha256:" + CanonicalObjectHasher.sha256(receipt));
        receipt.put("replayed", true);
        return receipt;
    }

    private record Fixture(
            ToolExecutionApplicationService toolExecution,
            OpsProjectMcpRuntimeConfigService runtimeConfigs,
            OpsProgressiveMcpInvocationService mcp,
            LandingOperationJournalApplicationService landingJournal,
            ChangePackageLandingProcessManager landingProcessManager,
            OpsToolSideEffectRecoveryService service) {
    }
}
