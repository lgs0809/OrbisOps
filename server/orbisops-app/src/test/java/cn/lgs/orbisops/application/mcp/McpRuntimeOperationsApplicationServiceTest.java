package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaExposureTier;
import cn.lgs.orbisops.domain.mcp.model.McpToolCall;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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

class McpRuntimeOperationsApplicationServiceTest {

    private SelectRuntimeMcpToolsQuery runtimeTools;
    private McpProjectToolCatalogPort catalog;
    private IMcpRuntimeCatalogRepository runtimeCatalog;
    private McpDiscoveryPort discovery;
    private McpRuntimeAuditPort audit;
    private McpRuntimePayloadSanitizerPort sanitizer;

    @BeforeEach
    void setUp() {
        runtimeTools = mock(SelectRuntimeMcpToolsQuery.class);
        catalog = mock(McpProjectToolCatalogPort.class);
        runtimeCatalog = mock(IMcpRuntimeCatalogRepository.class);
        discovery = mock(McpDiscoveryPort.class);
        audit = mock(McpRuntimeAuditPort.class);
        sanitizer = mock(McpRuntimePayloadSanitizerPort.class);
        when(runtimeCatalog.available()).thenReturn(true);
    }

    @Test
    void runtimeCatalogAndExecutableToolsDelegateToTypedQuery() {
        List<Map<String, Object>> catalogView = List.of(Map.of("toolName", "search"));
        List<Map<String, Object>> executable = List.of(Map.of("toolName", "search", "policyId", "policy-1"));
        when(runtimeTools.runtimeCatalog("project-1", "mcp-1", List.of("search"), List.of()))
                .thenReturn(catalogView);
        when(runtimeTools.runtimeExecutableTools("project-1", "mcp-1", List.of("search"), List.of()))
                .thenReturn(executable);

        McpRuntimeOperationsApplicationService service = service(true);

        assertEquals(catalogView, service.runtimeCatalog(
                "project-1", "mcp-1", List.of("search"), List.of()));
        assertEquals(executable, service.runtimeExecutableTools(
                "project-1", "mcp-1", List.of("search"), List.of()));
    }

    @Test
    void activationHydratesReviewedPolicyAndPersistsRunScopedLease() {
        McpAuthoritativeToolDefinition authoritative =
                new McpAuthoritativeToolDefinition(
                        "search logs",
                        Map.of("type", "object"),
                        "REMOTE_MCP_TOOL_DEFINITION");
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(activeReadSchema());
        McpRuntimeOperationsApplicationService service = service(true);
        McpRuntimeActivationRequest command = new McpRuntimeActivationRequest(
                "project-1", "run-1", "alice", "mcp-1", "search",
                "session-1", "agent-1", "alice", "investigate",
                "PRE_APPROVAL_WORKFLOW", false, authoritative);

        McpRuntimeActivationResult result = service.activateRuntimeTool(command);

        assertEquals("run-1", result.runId());
        assertEquals("schema-1", result.schemaHash());
        verify(discovery).hydrateSchema(argThat((McpSchemaHydrationRequest hydration) ->
                "project-1".equals(hydration.projectId())
                        && "mcp-1".equals(hydration.toolId())
                        && "search".equals(hydration.remoteToolName())
                        && authoritative.equals(hydration.authoritativeDefinition())));
        ArgumentCaptor<McpRuntimeActivation> activation = ArgumentCaptor.forClass(McpRuntimeActivation.class);
        verify(runtimeCatalog).saveActivation(activation.capture());
        assertEquals("project-1", activation.getValue().projectId());
        assertEquals("run-1", activation.getValue().runId());
        assertEquals("mcp-1", activation.getValue().mcpId());
        assertEquals("search", activation.getValue().toolName());
        assertEquals(LocalDateTime.of(2026, 7, 20, 8, 0), activation.getValue().expiresAt());
        verify(audit).recordRuntimeEvent(eq("project-1"), eq("agent-1"), eq("alice"),
                eq("tool-disclosure"), eq("enable"), any(), eq("LOW"), eq("SUCCEEDED"), any());
    }

    @Test
    void activationAllowsSystemVerifiedPlatformReadonlyPolicy() {
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(schema(policy(
                        McpToolPolicyStatus.ACTIVE,
                        McpToolPolicyReviewStatus.SYSTEM_VERIFIED,
                        true,
                        "READ_EXTERNAL_STATE",
                        "TARGET_RESOURCE_READ",
                        "READ_ONLY",
                        false,
                        McpSchemaExposureTier.EXTENSION)));

        McpRuntimeActivationResult result = service(true).activateRuntimeTool(activationCommand());

        assertEquals("search", result.toolName());
        verify(runtimeCatalog).saveActivation(argThat(activation ->
                "run-1".equals(activation.runId()) && "search".equals(activation.toolName())));
    }

    @Test
    void activationRejectsPendingOrMutatingPolicies() {
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(schema(policy(
                        McpToolPolicyStatus.PENDING_REVIEW,
                        McpToolPolicyReviewStatus.SYSTEM_SUGGESTED,
                        true,
                        "READ_EXTERNAL_STATE",
                        "TARGET_RESOURCE_READ",
                        "READ_ONLY",
                        false,
                        McpSchemaExposureTier.EXTENSION)));
        McpRuntimeActivationRequest command = activationCommand();

        SecurityException pendingError = assertThrows(SecurityException.class,
                () -> service(true).activateRuntimeTool(command));
        assertTrue(pendingError.getMessage().contains("MCP_POLICY_NOT_ACTIVE"));

        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(schema(policy(
                        McpToolPolicyStatus.ACTIVE,
                        McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                        false,
                        "MUTATE_TARGET_RESOURCE",
                        "TARGET_RESOURCE_WRITE",
                        "PROD_MUTATING",
                        true,
                        McpSchemaExposureTier.EXTENSION)));

        SecurityException mutatingError = assertThrows(SecurityException.class,
                () -> service(true).activateRuntimeTool(command));
        assertTrue(mutatingError.getMessage().contains("MCP_TOOL_REQUIRES_CHANGE_PACKAGE"));
    }

    @Test
    void trustedLandingActivationAllowsHumanReviewedLandAllowedMutatingPolicy() {
        McpToolPolicyProjection landingPolicy = new McpToolPolicyProjection(
                "policy-restart",
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "EXECUTE_EXTERNAL_ACTION",
                "PRODUCTION",
                "PROD_MUTATING",
                "MUTATING",
                List.of("RESTART"),
                cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.HIGH,
                false, false, false, true,
                true, true, true, true,
                McpSchemaExposureTier.EXTENSION,
                Map.of("allowedServices", List.of("order-service")));
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(schema(landingPolicy));
        McpRuntimeActivationRequest command = new McpRuntimeActivationRequest(
                "project-1", "landing-run-1", "ops-agent", "mcp-1", "restart_service",
                "landing-run-1", "platform-landing-react", "ops-agent", "approved landing",
                "LANDING", true, McpAuthoritativeToolDefinition.empty());

        McpRuntimeActivationResult result = service(true).activateRuntimeTool(command);

        assertEquals("restart_service", result.toolName());
        verify(runtimeCatalog).saveActivation(argThat(activation ->
                "landing-run-1".equals(activation.runId())
                        && "restart_service".equals(activation.toolName())));
    }

    @Test
    void externalLandingStageWithoutTrustedMarkerStillCannotActivateMutatingPolicy() {
        McpToolPolicyProjection landingPolicy = new McpToolPolicyProjection(
                "policy-restart",
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "EXECUTE_EXTERNAL_ACTION",
                "PRODUCTION",
                "PROD_MUTATING",
                "MUTATING",
                List.of("RESTART"),
                cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.HIGH,
                false, false, false, true,
                true, true, true, true,
                McpSchemaExposureTier.EXTENSION,
                Map.of());
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(schema(landingPolicy));
        McpRuntimeActivationRequest command = new McpRuntimeActivationRequest(
                "project-1", "run-1", "alice", "mcp-1", "restart_service",
                "", "", "alice", "", "LANDING", false,
                McpAuthoritativeToolDefinition.empty());

        SecurityException error = assertThrows(SecurityException.class,
                () -> service(true).activateRuntimeTool(command));

        assertTrue(error.getMessage().contains("MCP_TOOL_REQUIRES_CHANGE_PACKAGE"));
    }

    @Test
    void activationCheckIsScopedAndFailsClosedWhenStoreUnavailable() {
        when(runtimeCatalog.isActivationActive("project-1", "run-1", "mcp-1", "search")).thenReturn(true);
        McpRuntimeOperationsApplicationService service = service(true);

        assertTrue(service.isRuntimeToolActivated(new McpCommands.RuntimeActivationCheck(
                "project-1", "run-1", "mcp-1", "search")));
        verify(runtimeCatalog).isActivationActive("project-1", "run-1", "mcp-1", "search");

        when(runtimeCatalog.available()).thenReturn(false);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.isRuntimeToolActivated(new McpCommands.RuntimeActivationCheck(
                        "project-1", "run-1", "mcp-1", "search")));
        assertEquals("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE", error.getMessage());
    }

    @Test
    void disclosureConfigurationDistinguishesCoreAndExtensionTools() {
        assertTrue(service(true).requiresRuntimeActivation(schema(policy(
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                true, "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY",
                false, McpSchemaExposureTier.EXTENSION))));
        assertFalse(service(true).requiresRuntimeActivation(schema(policy(
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                true, "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY",
                false, McpSchemaExposureTier.CORE))));
        assertFalse(service(false).requiresRuntimeActivation(schema(policy(
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                true, "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY",
                false, McpSchemaExposureTier.EXTENSION))));
    }

    @Test
    void callRecordUsesCatalogMetadataAndSanitizedPayloads() {
        McpProjectToolDescriptor definition = new McpProjectToolDescriptor(
                "project-1", "tool-1", "search", "mysql", "stdio",
                List.of(), cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.MEDIUM,
                true, Map.of(), 30, ProjectMcpStatus.ENABLED, Map.of());
        when(catalog.list("project-1")).thenReturn(List.of(definition));
        when(sanitizer.sanitize(Map.of("query", "5xx"))).thenReturn("{\"query\":\"5xx\"}");
        when(sanitizer.sanitize(Map.of("result", "failed"))).thenReturn("{\"result\":\"masked\"}");
        McpCommands.RuntimeCall command = new McpCommands.RuntimeCall(
                "project-1", "agent-1", "node-1", "run-1", "tool-1", "", "",
                "FAILED", Map.of("query", "5xx"), Map.of("result", "failed"), 25L, Map.of());

        service(true).recordMcpCall(command);

        ArgumentCaptor<McpToolCall> call = ArgumentCaptor.forClass(McpToolCall.class);
        verify(runtimeCatalog).saveToolCall(call.capture());
        assertEquals("tool-1", call.getValue().mcpId());
        assertEquals("search", call.getValue().toolName());
        assertEquals("MEDIUM", call.getValue().riskLevel());
        assertTrue(call.getValue().readOnly());
        assertEquals("{\"query\":\"5xx\"}", call.getValue().inputJson());
        assertEquals("{\"result\":\"masked\"}", call.getValue().outputJson());
        assertEquals("{\"result\":\"masked\"}", call.getValue().errorMessage());
        verify(audit).recordRuntimeEvent(eq("project-1"), eq("agent-1"), eq(""),
                eq("mcp-tool-call"), eq("call"), any(), eq("MEDIUM"), eq("FAILED"), any());
    }

    @Test
    void routingWarningPreservesTypedContext() {
        service(true).recordToolRoutingWarning(new McpCommands.RoutingWarning(
                "project-1", "agent-1", "node-1", "run-1", "mcp-1", "RESOURCE_WARN",
                Map.of("reason", "not authorized")));

        verify(audit).recordRuntimeEvent(eq("project-1"), eq("agent-1"), eq(""),
                eq("tool-router"), eq("resource-warn"), eq("mcp-1"), eq("MEDIUM"),
                eq("RESOURCE_WARN"), argThat(payload ->
                        "run-1".equals(payload.get("runId"))
                                && "not authorized".equals(payload.get("reason"))));
    }

    private McpRuntimeOperationsApplicationService service(boolean disclosureEnabled) {
        return new McpRuntimeOperationsApplicationService(runtimeTools, catalog, runtimeCatalog,
                discovery, audit, new McpJsonCodec(), sanitizer, disclosureEnabled,
                Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC));
    }

    private McpRuntimeActivationRequest activationCommand() {
        return new McpRuntimeActivationRequest(
                "project-1", "run-1", "alice", "mcp-1", "search",
                "", "", "alice", "", "PRE_APPROVAL_WORKFLOW", false,
                McpAuthoritativeToolDefinition.empty());
    }

    private McpHydratedToolSchema activeReadSchema() {
        return schema(policy(
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                true,
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                false,
                McpSchemaExposureTier.EXTENSION));
    }

    private McpHydratedToolSchema schema(McpToolPolicyProjection policy) {
        return new McpHydratedToolSchema(
                "project-1", "mcp-1", "mcp-1", "search", "search",
                "logs", "stdio", 30, List.of("READ"),
                cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.LOW,
                policy.readOnly(), true, Map.of(), true,
                Map.of("type", "object"), "search logs",
                "REMOTE_MCP_TOOL_DEFINITION", "schema-1", policy);
    }

    private McpToolPolicyProjection policy(
            McpToolPolicyStatus status,
            McpToolPolicyReviewStatus reviewStatus,
            boolean readOnly,
            String effectType,
            String effectScope,
            String mutability,
            boolean requiresApprovedPackage,
            McpSchemaExposureTier exposureTier) {
        return new McpToolPolicyProjection(
                "policy-1", status, reviewStatus,
                effectType, effectScope, mutability,
                readOnly ? "READ_ONLY" : "MUTATING",
                List.of(readOnly ? "READ" : "UPDATE"),
                readOnly
                        ? cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.LOW
                        : cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.HIGH,
                readOnly, readOnly, readOnly, false,
                requiresApprovedPackage, requiresApprovedPackage,
                requiresApprovedPackage, requiresApprovedPackage,
                exposureTier, Map.of());
    }

}
