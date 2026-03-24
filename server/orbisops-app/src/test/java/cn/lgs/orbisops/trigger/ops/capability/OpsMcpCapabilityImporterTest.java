package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.project.ProjectExternalMcpApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpCapabilityImporterTest {

    @Test
    void registersDiscoversHydratesAndProjectsPendingReviewResult() {
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        ProjectExternalMcpApplicationService external =
                mock(ProjectExternalMcpApplicationService.class);
        OpsProjectMcpRuntimeConfigService runtimeConfig =
                mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        ProgressiveMcpProcessManager progressive =
                mock(ProgressiveMcpProcessManager.class);
        URI endpoint = URI.create("https://mcp.example.com/sse");
        when(urlPolicy.validate(endpoint.toString())).thenReturn(endpoint);
        when(external.register(
                eq("project-1"),
                anyString(),
                eq("Payments MCP"),
                eq(endpoint.toString()),
                eq("sse"),
                eq("vault://mcp/token"),
                eq("BEARER")))
                .thenAnswer(invocation -> Map.of(
                        "mcpId", invocation.getArgument(1),
                        "status", "PENDING_REVIEW"));
        OpsMcpServerConfig server = OpsMcpServerConfig.builder()
                .mcpId("stored-mcp")
                .url(endpoint.toString())
                .transport("sse")
                .build();
        when(runtimeConfig.resolveForDiscovery(eq("project-1"), anyString()))
                .thenReturn(Optional.of(server));
        Map<String, Object> definition = Map.of(
                "toolName", "query_orders",
                "description", "query orders");
        when(toolProvider.inspectRemoteToolDefinitions(server))
                .thenReturn(List.of(definition));
        McpHydratedToolSchema hydratedSchema = mock(McpHydratedToolSchema.class);
        when(hydratedSchema.view()).thenReturn(Map.of(
                        "remoteToolName", "query_orders",
                        "schemaHash", "schema-hash",
                        "policyId", "policy-1",
                        "policyStatus", "DRAFT",
                        "reviewStatus", "PENDING_REVIEW"));
        when(progressive.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(hydratedSchema);
        when(external.recordDiscovery(
                eq("project-1"),
                anyString(),
                anyList(),
                eq("DISCOVERED"),
                eq("")))
                .thenAnswer(invocation -> Map.of(
                        "mcpId", invocation.getArgument(1),
                        "discoveryStatus", "DISCOVERED"));
        OpsMcpCapabilityImporter importer = new OpsMcpCapabilityImporter(
                urlPolicy,
                external,
                runtimeConfig,
                toolProvider,
                progressive);

        OpsMcpCapabilityImporter.Result result = importer.importCapability(
                new OpsMcpCapabilityImporter.Input(
                        "project-1",
                        endpoint.toString(),
                        "Payments MCP",
                        "vault://mcp/token",
                        ""));

        assertTrue(result.mcpId().startsWith("payments-mcp-"));
        assertEquals(endpoint.toString(), result.endpoint());
        assertEquals("vault://mcp/token", result.credentialRef());
        assertEquals("DISCOVERED_PENDING_REVIEW", result.discoveryStatus());
        assertEquals("", result.discoveryError());
        assertEquals(1, result.discoveredTools().size());
        assertEquals("query_orders", result.discoveredTools().get(0).get("toolName"));
        assertEquals("schema-hash", result.discoveredTools().get(0).get("schemaHash"));
        assertEquals("policy-1", result.discoveredTools().get(0).get("policyId"));
        assertEquals(1, result.policySuggestions().size());
        assertEquals("query_orders", result.policySuggestions().get(0).get("toolName"));
        assertEquals("PENDING_REVIEW", result.policySuggestions().get(0).get("reviewStatus"));

        ArgumentCaptor<McpSchemaHydrationRequest> hydration =
                ArgumentCaptor.forClass(McpSchemaHydrationRequest.class);
        verify(progressive).hydrateSchema(hydration.capture());
        assertEquals("project-1", hydration.getValue().projectId());
        assertEquals(result.mcpId(), hydration.getValue().toolId());
        assertEquals("query_orders", hydration.getValue().remoteToolName());
        assertEquals("query orders",
                hydration.getValue().authoritativeDefinition().description());
    }

    @Test
    void defaultsStreamableHttpAndNoneAuthForBlankOptionalFields() {
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        ProjectExternalMcpApplicationService external =
                mock(ProjectExternalMcpApplicationService.class);
        OpsProjectMcpRuntimeConfigService runtimeConfig =
                mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        ProgressiveMcpProcessManager progressive =
                mock(ProgressiveMcpProcessManager.class);
        URI endpoint = URI.create("https://mcp.example.com/api");
        when(urlPolicy.validate(endpoint.toString())).thenReturn(endpoint);
        when(external.register(
                eq("project-1"),
                anyString(),
                eq("mcp.example.com MCP"),
                eq(endpoint.toString()),
                eq("streamable-http"),
                eq(""),
                eq("NONE")))
                .thenAnswer(invocation -> Map.of("mcpId", invocation.getArgument(1)));
        OpsMcpServerConfig server = mock(OpsMcpServerConfig.class);
        when(runtimeConfig.resolveForDiscovery(eq("project-1"), anyString()))
                .thenReturn(Optional.of(server));
        when(toolProvider.inspectRemoteToolDefinitions(server)).thenReturn(List.of());
        when(external.recordDiscovery(
                eq("project-1"),
                anyString(),
                eq(List.of()),
                eq("DISCOVERED"),
                eq("")))
                .thenAnswer(invocation -> Map.of("mcpId", invocation.getArgument(1)));
        OpsMcpCapabilityImporter importer = new OpsMcpCapabilityImporter(
                urlPolicy,
                external,
                runtimeConfig,
                toolProvider,
                progressive);

        OpsMcpCapabilityImporter.Result result = importer.importCapability(
                new OpsMcpCapabilityImporter.Input(
                        "project-1",
                        endpoint.toString(),
                        "",
                        "",
                        ""));

        assertTrue(result.mcpId().startsWith("mcp-example-com-mcp-"));
        assertEquals("DISCOVERED_PENDING_REVIEW", result.discoveryStatus());
        assertTrue(result.discoveredTools().isEmpty());
        assertTrue(result.policySuggestions().isEmpty());
    }

    @Test
    void discoveryFailureIsPersistedAndReturnedWithoutUndoingRegistration() {
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        ProjectExternalMcpApplicationService external =
                mock(ProjectExternalMcpApplicationService.class);
        OpsProjectMcpRuntimeConfigService runtimeConfig =
                mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        ProgressiveMcpProcessManager progressive =
                mock(ProgressiveMcpProcessManager.class);
        URI endpoint = URI.create("https://mcp.example.com/api");
        when(urlPolicy.validate(endpoint.toString())).thenReturn(endpoint);
        when(external.register(
                eq("project-1"),
                anyString(),
                eq("MCP"),
                eq(endpoint.toString()),
                eq("streamable-http"),
                eq(""),
                eq("NONE")))
                .thenAnswer(invocation -> Map.of("mcpId", invocation.getArgument(1)));
        when(runtimeConfig.resolveForDiscovery(eq("project-1"), anyString()))
                .thenThrow(new IllegalStateException("connection failed"));
        when(external.recordDiscovery(
                eq("project-1"),
                anyString(),
                eq(List.of()),
                eq("DISCOVERY_FAILED"),
                eq("connection failed")))
                .thenAnswer(invocation -> Map.of(
                        "mcpId", invocation.getArgument(1),
                        "discoveryStatus", "DISCOVERY_FAILED"));
        OpsMcpCapabilityImporter importer = new OpsMcpCapabilityImporter(
                urlPolicy,
                external,
                runtimeConfig,
                toolProvider,
                progressive);

        OpsMcpCapabilityImporter.Result result = importer.importCapability(
                new OpsMcpCapabilityImporter.Input(
                        "project-1",
                        endpoint.toString(),
                        "MCP",
                        "",
                        ""));

        assertEquals("DISCOVERY_FAILED", result.discoveryStatus());
        assertEquals("connection failed", result.discoveryError());
        assertTrue(result.discoveredTools().isEmpty());
        assertTrue(result.policySuggestions().isEmpty());
        assertFalse(result.mcp().isEmpty());
        verify(external).register(
                eq("project-1"),
                eq(result.mcpId()),
                eq("MCP"),
                eq(endpoint.toString()),
                eq("streamable-http"),
                eq(""),
                eq("NONE"));
    }

    @Test
    void missingRegistrationFailsBeforeWriteAndMissingDiscoveryPreservesRegistration() {
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        URI endpoint = URI.create("https://mcp.example.com/api");
        when(urlPolicy.validate(endpoint.toString())).thenReturn(endpoint);
        OpsMcpCapabilityImporter missingRegistration = new OpsMcpCapabilityImporter(
                urlPolicy,
                null,
                null,
                null,
                null);

        assertEquals(
                "PROJECT_EXTERNAL_MCP_SERVICE_NOT_CONFIGURED",
                assertThrows(
                        IllegalStateException.class,
                        () -> missingRegistration.importCapability(
                                new OpsMcpCapabilityImporter.Input(
                                        "project-1",
                                        endpoint.toString(),
                                        "MCP",
                                        "",
                                        "")))
                        .getMessage());

        ProjectExternalMcpApplicationService external =
                mock(ProjectExternalMcpApplicationService.class);
        when(external.register(
                eq("project-1"),
                anyString(),
                eq("MCP"),
                eq(endpoint.toString()),
                eq("streamable-http"),
                eq(""),
                eq("NONE")))
                .thenAnswer(invocation -> Map.of("mcpId", invocation.getArgument(1)));
        when(external.recordDiscovery(
                eq("project-1"),
                anyString(),
                eq(List.of()),
                eq("DISCOVERY_FAILED"),
                eq("MCP_DISCOVERY_SERVICE_NOT_CONFIGURED")))
                .thenAnswer(invocation -> Map.of(
                        "mcpId", invocation.getArgument(1),
                        "connectionStatus", "DISCOVERY_FAILED"));
        OpsMcpCapabilityImporter missingDiscovery = new OpsMcpCapabilityImporter(
                urlPolicy,
                external,
                null,
                null,
                null);
        OpsMcpCapabilityImporter.Result result =
                missingDiscovery.importCapability(
                        new OpsMcpCapabilityImporter.Input(
                                "project-1",
                                endpoint.toString(),
                                "MCP",
                                "",
                                ""));

        assertEquals("DISCOVERY_FAILED", result.discoveryStatus());
        assertEquals(
                "MCP_DISCOVERY_SERVICE_NOT_CONFIGURED",
                result.discoveryError());
        assertTrue(result.discoveredTools().isEmpty());
        assertTrue(result.policySuggestions().isEmpty());
        verify(external).recordDiscovery(
                eq("project-1"),
                eq(result.mcpId()),
                eq(List.of()),
                eq("DISCOVERY_FAILED"),
                eq("MCP_DISCOVERY_SERVICE_NOT_CONFIGURED"));
    }
}
