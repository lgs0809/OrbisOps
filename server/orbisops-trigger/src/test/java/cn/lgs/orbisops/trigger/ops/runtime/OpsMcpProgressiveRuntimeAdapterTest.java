package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionRequest;
import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionResult;
import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpProgressiveRuntimeAdapterTest {

    @Test
    void managedExposureMustUseRuntimeCatalogAndTypedDisclosureFlags() {
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        OpsMcpServerConfig config = config(true);
        when(processManager.runtimeExecutableTools(
                eq("project-1"), eq("tool-1"), any(), any(), any()))
                .thenReturn(List.of(
                        Map.of("toolName", "search_logs", "disclosureTier", "CORE"),
                        Map.of("toolName", "update_config", "disclosureTier", "EXTENSION"),
                        Map.of("toolName", "search_logs", "disclosureTier", "CORE")));
        OpsMcpProgressiveRuntimeAdapter adapter = adapter(
                processManager,
                OpsMcpProgressiveSettings.forTest(true, true));

        OpsMcpProgressiveExposure exposure = adapter.exposure(config);

        assertTrue(exposure.managed());
        assertFalse(exposure.blocked());
        assertTrue(exposure.disclosureEnabled());
        assertTrue(exposure.extensionAvailable());
        assertEquals(List.of("search_logs", "update_config"), exposure.allowedToolNames());
        verify(processManager).summary("project-1");
    }

    @Test
    void emptyRuntimeCatalogAndLegacyProjectExposureMustFailClosedWithWarnings() {
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        when(processManager.runtimeExecutableTools(
                eq("project-1"), eq("tool-1"), any(), any(), any()))
                .thenReturn(List.of());
        OpsMcpProgressiveRuntimeAdapter adapter = adapter(
                processManager,
                OpsMcpProgressiveSettings.forTest(true, false));

        OpsMcpProgressiveExposure empty = adapter.exposure(config(true));
        OpsMcpProgressiveExposure legacyProject = adapter.exposure(config(false));

        assertTrue(empty.blocked());
        assertTrue(legacyProject.blocked());
        verify(processManager).recordToolRoutingWarning(argThat((McpCommands.RoutingWarning warning) ->
                "NO_ACTIVE_RUNTIME_MCP_TOOL".equals(warning.payload().get("reason"))));
        verify(processManager).recordToolRoutingWarning(argThat((McpCommands.RoutingWarning warning) ->
                "PROJECT_MANAGED_MCP_REQUIRED".equals(warning.payload().get("reason"))));
    }

    @Test
    void missingManagerMayFallbackToLegacyOnlyWhenEnforcementDisabled() {
        OpsMcpProgressiveRuntimeAdapter enforcing = adapter(
                null,
                OpsMcpProgressiveSettings.forTest(true, false));
        OpsMcpProgressiveRuntimeAdapter permissive = adapter(
                null,
                OpsMcpProgressiveSettings.forTest(false, false));

        assertTrue(enforcing.exposure(config(true)).blocked());
        assertEquals(OpsMcpProgressiveExposure.Mode.LEGACY,
                permissive.exposure(config(true)).mode());
        assertThrows(IllegalStateException.class,
                () -> permissive.selectAndHydrate(config(true), "search_logs"));
    }

    @Test
    void selectAndHydrateMustUseTypedStageAndRemoteToolIdentity() {
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        McpDiscoverySelectionResult selected = mock(McpDiscoverySelectionResult.class);
        when(selected.selected()).thenReturn(true);
        when(processManager.select(any(McpDiscoverySelectionRequest.class)))
                .thenReturn(selected);
        Map<String, Object> schema = Map.of(
                "policyStatus", "ACTIVE",
                "reviewStatus", "HUMAN_REVIEWED");
        McpHydratedToolSchema hydratedSchema = mock(McpHydratedToolSchema.class);
        when(hydratedSchema.view()).thenReturn(schema);
        when(processManager.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(hydratedSchema);
        OpsMcpServerConfig config = config(true);
        config.setToolCallStage("PREPARE");
        config.setToolCapabilities(Map.of("search_logs", "read_only"));
        OpsMcpProgressiveRuntimeAdapter adapter = adapter(
                processManager,
                OpsMcpProgressiveSettings.forTest(true, false));

        Map<String, Object> hydrated = adapter.selectAndHydrate(config, "search_logs");

        assertEquals(schema, hydrated);
        verify(processManager).select(argThat((McpDiscoverySelectionRequest request) ->
                "project-1".equals(request.projectId())
                        && "PREPARE".equals(request.stage())
                        && "read_only".equals(request.capability())));
        verify(processManager).hydrateSchema(argThat((McpSchemaHydrationRequest request) ->
                "search_logs".equals(request.remoteToolName())
                        && "tool-1".equals(request.toolId())
                        && "agent-1".equals(request.agentId())));
    }

    @Test
    void routeMissMustFailClosedAndPreRemoteAuditMustPreserveBlockMetadata() {
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        McpDiscoverySelectionResult skipped = mock(McpDiscoverySelectionResult.class);
        when(skipped.selected()).thenReturn(false);
        when(processManager.select(any(McpDiscoverySelectionRequest.class)))
                .thenReturn(skipped);
        OpsMcpProgressiveRuntimeAdapter adapter = adapter(
                processManager,
                OpsMcpProgressiveSettings.forTest(true, false));

        SecurityException routeMiss = assertThrows(SecurityException.class,
                () -> adapter.selectAndHydrate(config(true), "search_logs"));
        assertTrue(routeMiss.getMessage().contains("工具路由未命中"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyId", "policy-1");
        adapter.recordPreRemoteFailure(
                config(true),
                "update_config",
                "{\"token\":\"<redacted>\"}",
                new SecurityException("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：required"),
                12L,
                metadata);

        verify(processManager).recordMcpCall(argThat((McpCommands.RuntimeCall call) ->
                "BLOCKED".equals(call.status())
                        && "update_config".equals(call.toolName())
                        && "MCP_TOOL_REQUIRES_CHANGE_PACKAGE".equals(
                        call.metadata().get("blockReason"))
                        && "policy-1".equals(call.metadata().get("policyId"))));
    }

    @Test
    void settingsAndAuditMustRemainExplicitAndBestEffort() {
        OpsMcpProgressiveSettings settings = OpsMcpProgressiveSettings.forTest(false, true);
        assertFalse(settings.enforceProjectManaged());
        assertTrue(settings.disclosureEnabled());

        OpsMcpProgressiveRuntimeAdapter adapter = new OpsMcpProgressiveRuntimeAdapter(
                () -> {
                    throw new IllegalStateException("audit unavailable");
                },
                settings,
                policy());
        assertDoesNotThrow(() -> adapter.recordPreRemoteFailure(
                config(true),
                "search_logs",
                "{}",
                new IllegalStateException("failed"),
                1L,
                Map.of()));
    }

    private OpsMcpProgressiveRuntimeAdapter adapter(
            ProgressiveMcpProcessManager processManager,
            OpsMcpProgressiveSettings settings) {
        return new OpsMcpProgressiveRuntimeAdapter(
                () -> processManager,
                settings,
                policy());
    }

    private OpsMcpRemoteCallPolicy policy() {
        return new OpsMcpRemoteCallPolicy(new OpsMcpToolArgumentPolicyChecker());
    }

    private OpsMcpServerConfig config(boolean progressiveManaged) {
        return OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .projectId("project-1")
                .runId("run-1")
                .agentId("agent-1")
                .nodeId("node-1")
                .mcpId("mcp-1")
                .toolId("tool-1")
                .progressiveManaged(progressiveManaged)
                .allowedTools(List.of("search_logs"))
                .blockedTools(List.of())
                .build();
    }
}
