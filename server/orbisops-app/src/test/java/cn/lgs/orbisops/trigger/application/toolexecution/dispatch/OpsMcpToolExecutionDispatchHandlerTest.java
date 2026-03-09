package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionDeniedException;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSchema;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpToolExecutionDispatchHandlerTest {

    @Test
    void supportsOnlyMcpProviderAndInvokesMcpApplicationWithExactRemoteIdentity() {
        McpExecutionApplicationService application = mock(McpExecutionApplicationService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        when(configs.resolve("project-1", "ops-mcp")).thenReturn(Optional.of(config()));
        when(application.execute(any())).thenReturn(response(true, "ALLOWED"));
        OpsMcpToolExecutionDispatchHandler handler = handler(application, configs);

        assertTrue(handler.supports(target()));
        assertFalse(handler.supports(localTarget()));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) handler.dispatch(target(), request(false));

        assertEquals("MCP", result.get("providerType"));
        assertEquals("ops-mcp", result.get("providerId"));
        assertEquals("search_logs", result.get("remoteToolName"));
        assertEquals("provider-result", result.get("resultId"));

        ArgumentCaptor<McpExecutionRequest> captor = ArgumentCaptor.forClass(McpExecutionRequest.class);
        verify(application).execute(captor.capture());
        McpExecutionRequest mapped = captor.getValue();
        assertEquals("ops-mcp", mapped.config().mcpId());
        assertEquals("run-1", mapped.config().runId());
        assertEquals("agent-1", mapped.config().agentId());
        assertEquals("node-1", mapped.config().nodeId());
        assertEquals("search_logs", mapped.requestedToolName());
        assertEquals(Map.of("query", "error"), mapped.input().get("arguments"));
        assertFalse(mapped.trustedLandingRuntime());
    }

    @Test
    void approvedLandingCopiesPackageAuthorityIntoMcpRequest() {
        McpExecutionApplicationService application = mock(McpExecutionApplicationService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        when(configs.resolve("project-1", "ops-mcp")).thenReturn(Optional.of(config()));
        when(application.execute(any())).thenReturn(response(true, "ALLOWED"));
        OpsMcpToolExecutionDispatchHandler handler = handler(application, configs);

        handler.dispatch(target(), request(true));

        ArgumentCaptor<McpExecutionRequest> captor = ArgumentCaptor.forClass(McpExecutionRequest.class);
        verify(application).execute(captor.capture());
        McpExecutionRequest mapped = captor.getValue();
        assertTrue(mapped.trustedLandingRuntime());
        assertTrue(mapped.config().landingApproved());
        assertEquals("package-1", mapped.config().changePackageId());
        assertEquals("hash-1", mapped.config().approvedPackageHash());
        assertEquals(3, mapped.config().approvedPackageVersion());
        assertEquals("operation-1", mapped.config().operationId());
    }

    @Test
    void approvedLandingInjectsAndOverridesRuntimeOwnedRemoteArguments() {
        McpExecutionApplicationService application = mock(McpExecutionApplicationService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        when(configs.resolve("project-1", "ops-mcp")).thenReturn(Optional.of(config()));
        when(application.execute(any())).thenReturn(response(true, "ALLOWED"));
        OpsMcpToolExecutionDispatchHandler handler = handler(application, configs);
        Instant deadline = Instant.parse("2026-08-13T20:00:00Z");
        ToolExecutionRequest request = new ToolExecutionRequest(
                "project-1",
                "alice",
                "alice",
                "observability.logs",
                "project_logs_search",
                ToolExecutionScope.APPROVED_LANDING,
                Map.of(
                        "service", "order-service",
                        "projectId", "spoofed-project",
                        "actor", "mallory",
                        "executionKey", "spoofed-key",
                        "deadline", "2000-01-01T00:00:00Z"),
                "session-1",
                "run-1",
                Map.of(
                        "agentId", "agent-1",
                        "nodeId", "node-1",
                        "idempotencyKey", "trusted-execution-key",
                        "authorityDeadline", deadline),
                Map.of(
                        "changePackageId", "package-1",
                        "approvedPackageHash", "hash-1",
                        "approvedPackageVersion", 3,
                        "operationId", "operation-1"));

        handler.dispatch(target(), request);

        ArgumentCaptor<McpExecutionRequest> captor = ArgumentCaptor.forClass(McpExecutionRequest.class);
        verify(application).execute(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) captor.getValue().input().get("arguments");
        assertEquals("order-service", arguments.get("service"));
        assertEquals("project-1", arguments.get("projectId"));
        assertEquals("alice", arguments.get("actor"));
        assertEquals("trusted-execution-key", arguments.get("executionKey"));
        assertEquals(deadline.toString(), arguments.get("deadline"));
    }

    @Test
    void missingRuntimeConfigFailsClosedBeforeMcpApplication() {
        McpExecutionApplicationService application = mock(McpExecutionApplicationService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        when(configs.resolve("project-1", "ops-mcp")).thenReturn(Optional.empty());
        OpsMcpToolExecutionDispatchHandler handler = handler(application, configs);

        SecurityException error = assertThrows(SecurityException.class,
                () -> handler.dispatch(target(), request(false)));

        assertTrue(error.getMessage().contains("MCP_RUNTIME_CONFIG_NOT_FOUND"));
    }

    @Test
    void mcpDenialReturnsProviderEvidenceInTypedSecurityException() {
        McpExecutionApplicationService application = mock(McpExecutionApplicationService.class);
        OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        when(configs.resolve("project-1", "ops-mcp")).thenReturn(Optional.of(config()));
        McpExecutionResponse denied = response(false, "BLOCKED");
        when(application.execute(any())).thenThrow(
                new McpExecutionDeniedException("MCP_TOOL_NOT_ALLOWED：blocked", denied));
        OpsMcpToolExecutionDispatchHandler handler = handler(application, configs);

        OpsMcpToolExecutionDispatchHandler.OpsMcpToolExecutionDeniedException error =
                assertThrows(
                        OpsMcpToolExecutionDispatchHandler.OpsMcpToolExecutionDeniedException.class,
                        () -> handler.dispatch(target(), request(false)));

        assertEquals(false, error.providerResult().get("allowed"));
        assertEquals("provider-result", error.providerResult().get("resultId"));
        assertEquals("ops-mcp", error.providerResult().get("providerId"));
    }

    @Test
    void missingExactProviderIdentityFailsClosed() {
        ToolProviderDescriptor provider = new ToolProviderDescriptor(
                ToolProviderType.MCP, "MCP", "MCP", "", "", "", "{}", "{}");
        ToolExecutionTarget target = new ToolExecutionTarget(
                "observability.traces", "trace_search", "MCP", "LOW",
                true, false, false, false, false,
                provider, ToolSemantics.readOnlyTool(), ToolSchema.empty());
        OpsMcpToolExecutionDispatchHandler handler = handler(
                mock(McpExecutionApplicationService.class),
                mock(OpsProjectMcpRuntimeConfigService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> handler.dispatch(target, request(false)));

        assertTrue(error.getMessage().contains("MCP_PROVIDER_ID_REQUIRED"));
    }

    private OpsMcpToolExecutionDispatchHandler handler(
            McpExecutionApplicationService application,
            OpsProjectMcpRuntimeConfigService configs) {
        return new OpsMcpToolExecutionDispatchHandler(
                application, new OpsMcpExecutionMapper(), configs);
    }

    private ToolExecutionTarget target() {
        ToolProviderDescriptor provider = new ToolProviderDescriptor(
                ToolProviderType.MCP,
                "ops-mcp",
                "MCP",
                "",
                "ops-mcp",
                "search_logs",
                "{}",
                "{}");
        return new ToolExecutionTarget(
                "observability.logs",
                "project_logs_search",
                "MCP",
                ToolRiskLevel.LOW.name(),
                true, false, false, false, false,
                provider,
                ToolSemantics.readOnlyTool(),
                ToolSchema.inputOnly("{\"type\":\"object\"}"));
    }

    private ToolExecutionTarget localTarget() {
        return new ToolExecutionTarget(
                "db.mysql.readonly", "mysql_query_readonly", "LOCAL_MYSQL", "LOW",
                true, false, false, false, false);
    }

    private ToolExecutionRequest request(boolean landing) {
        Map<String, Object> authority = landing
                ? Map.of(
                        "changePackageId", "package-1",
                        "approvedPackageHash", "hash-1",
                        "approvedPackageVersion", 3,
                        "operationId", "operation-1")
                : Map.of();
        return new ToolExecutionRequest(
                "project-1",
                "alice",
                "alice",
                "observability.logs",
                "project_logs_search",
                landing ? ToolExecutionScope.APPROVED_LANDING : ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                Map.of("query", "error"),
                "session-1",
                "run-1",
                Map.of("agentId", "agent-1", "nodeId", "node-1"),
                authority);
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("Ops MCP")
                .projectId("project-1")
                .mcpId("ops-mcp")
                .transport("streamable-http")
                .url("https://mcp.example.test")
                .timeoutSeconds(30)
                .allowedTools(List.of("search_logs"))
                .toolCapabilities(Map.of("search_logs", "read_only"))
                .build();
    }

    private McpExecutionResponse response(boolean allowed, String decision) {
        return new McpExecutionResponse(
                allowed,
                decision,
                "PRE_APPROVAL_WORKFLOW",
                "mcp.ops-mcp",
                "search_logs",
                new McpExecutionRecordedResult(
                        "provider-result",
                        "provider-evidence",
                        "preview",
                        "a".repeat(64),
                        false,
                        "db:provider-result",
                        "b".repeat(64),
                        12L),
                Map.of("rawPreview", "ok"));
    }
}
