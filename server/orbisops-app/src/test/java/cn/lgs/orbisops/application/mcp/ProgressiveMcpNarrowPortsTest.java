package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgressiveMcpNarrowPortsTest {

    @Test
    void processManagerRoutesEachConcernToItsDedicatedPort() {
        McpSummaryPort summaries = mock(McpSummaryPort.class);
        McpDiscoveryPort discovery = mock(McpDiscoveryPort.class);
        McpRuntimeOperationsPort runtime = mock(McpRuntimeOperationsPort.class);
        ProgressiveMcpProcessManager manager = new ProgressiveMcpProcessManager(summaries, discovery, runtime);
        McpDiscoverySelectionRequest select = new McpDiscoverySelectionRequest(
                "project-1", "logs", "", 3,
                "", "", "", "", "logs", false, "", Map.of("query", "logs"));
        McpDiscoverySelectionResult selection = mock(McpDiscoverySelectionResult.class);
        when(selection.selected()).thenReturn(true);
        McpRuntimeActivationRequest activation = new McpRuntimeActivationRequest(
                "project-1", "run-1", "alice", "logs-mcp", "search",
                "", "", "alice", "", "PRE_APPROVAL_WORKFLOW", false,
                McpAuthoritativeToolDefinition.empty());
        McpRuntimeActivationResult activationResult = new McpRuntimeActivationResult(
                "activation-1", "run-1", "logs-mcp", "search",
                "schema-1", Map.of("type", "object"), "search logs", Map.of());
        when(summaries.summary("project-1")).thenReturn(Map.of("status", "READY"));
        when(discovery.select(select)).thenReturn(selection);
        when(runtime.activateRuntimeTool(activation)).thenReturn(activationResult);
        when(runtime.runtimeCatalog("project-1", "logs-mcp", List.of("search"), List.of()))
                .thenReturn(List.of(Map.of("toolName", "search")));

        assertEquals("READY", manager.summary("project-1").get("status"));
        assertTrue(manager.select(select).selected());
        assertEquals("activation-1", manager.activateRuntimeTool(activation).activationId());
        assertEquals("search", manager.runtimeCatalog(
                "project-1", "logs-mcp", List.of("search"), List.of()).get(0).get("toolName"));

        verify(summaries).summary("project-1");
        verify(discovery).select(select);
        verify(runtime).activateRuntimeTool(activation);
        verify(runtime).runtimeCatalog("project-1", "logs-mcp", List.of("search"), List.of());
    }

    @Test
    void policyReviewUseCaseUsesOnlyPolicyCommandPort() {
        McpPolicyCommandPort policies = mock(McpPolicyCommandPort.class);
        ReviewMcpPolicyUseCase useCase = new ReviewMcpPolicyUseCase(policies);
        McpCommands.PolicyMutation command = new McpCommands.PolicyMutation(
                "project-1", "policy-1", "admin", McpToolPolicyPatch.empty());
        McpToolPolicy disabled = mock(McpToolPolicy.class);
        when(disabled.status()).thenReturn(McpToolPolicyStatus.DISABLED);
        when(policies.disableToolPolicy(command)).thenReturn(disabled);

        assertEquals(McpToolPolicyStatus.DISABLED, useCase.disable(command).status());
        verify(policies).disableToolPolicy(command);
    }

    @Test
    void progressivePortsHaveApplicationOwners() {
        assertTrue(McpDiscoveryPort.class.isAssignableFrom(McpDiscoveryApplicationService.class));
        assertTrue(McpRuntimeOperationsPort.class.isAssignableFrom(McpRuntimeOperationsApplicationService.class));
    }

    @Test
    void requiredPortsFailFast() {
        McpSummaryPort summaries = mock(McpSummaryPort.class);
        McpDiscoveryPort discovery = mock(McpDiscoveryPort.class);
        McpRuntimeOperationsPort runtime = mock(McpRuntimeOperationsPort.class);

        assertThrows(IllegalArgumentException.class,
                () -> new ProgressiveMcpProcessManager(null, discovery, runtime));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressiveMcpProcessManager(summaries, null, runtime));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressiveMcpProcessManager(summaries, discovery, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewMcpPolicyUseCase(null));
    }
}
