package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpDiscoveryPort;
import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionRequest;
import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionResult;
import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpRuntimeOperationsPort;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.McpSummaryPort;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionRemoteAdapter;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionRouterAdapter;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionRuntimeAdapter;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionCatalogAdapter;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsCompositeToolExecutionDispatchAdapter;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsMcpToolExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsMcpToolInvoker;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetCatalogService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpToolProviderSecurityTest {

    private final Map<McpRuntimeOperationsPort, Map<String, Object>> discoverySchemas = new IdentityHashMap<>();

    @Test
    void projectAgentWithEnforceDoesNotExposeLegacyRemoteCallbacks() {
        McpRuntimeOperationsPort progressive = mock(McpRuntimeOperationsPort.class);
        TestableProvider provider = provider(progressive, new AtomicBoolean());

        List<ToolCallback> callbacks = provider.buildToolCallbacks(List.of(config(false, List.of("searchLogs"), List.of())));

        assertTrue(callbacks.isEmpty());
        verify(progressive).recordToolRoutingWarning(argThat((McpCommands.RoutingWarning command) ->
                "project-1".equals(command.projectId())
                        && "agent-1".equals(command.agentId())
                        && "node-1".equals(command.nodeId())
                        && "run-1".equals(command.runId())
                        && "mcp-1".equals(command.resourceId())
                        && "RESOURCE_WARN".equals(command.status())));
    }

    @Test
    void progressiveManagedMcpOnlyExposesDispatcher() {
        McpRuntimeOperationsPort progressive = mock(McpRuntimeOperationsPort.class);
        when(progressive.runtimeExecutableTools(eq("project-1"), eq("tool-1"), any(), any(), any()))
                .thenReturn(List.of(runtimeTool("searchLogs")));
        TestableProvider provider = provider(progressive, new AtomicBoolean());

        List<ToolCallback> callbacks = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of())));

        assertEquals(1, callbacks.size());
        assertTrue(callbacks.get(0).getToolDefinition().name().startsWith("project_mcp_"));
    }

    @Test
    void progressiveManagedMcpWithNoActiveRuntimePolicyDoesNotExposeDispatcher() {
        McpRuntimeOperationsPort progressive = mock(McpRuntimeOperationsPort.class);
        when(progressive.runtimeExecutableTools(eq("project-1"), eq("tool-1"), any(), any(), any()))
                .thenReturn(List.of());
        TestableProvider provider = provider(progressive, new AtomicBoolean());

        List<ToolCallback> callbacks = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of())));

        assertFalse(callbacks.stream().anyMatch(callback -> callback.getToolDefinition().name().startsWith("project_mcp_")));
        verify(progressive).recordToolRoutingWarning(argThat((McpCommands.RoutingWarning command) ->
                "project-1".equals(command.projectId())
                        && "agent-1".equals(command.agentId())
                        && "node-1".equals(command.nodeId())
                        && "run-1".equals(command.runId())
                        && "mcp-1".equals(command.resourceId())
                        && "RESOURCE_WARN".equals(command.status())));
    }

    @Test
    void unauthorizedRemoteToolIsBlockedAndAudited() {
        McpRuntimeOperationsPort progressive = routedProgressive(schema(true, "LOW", List.of("READ")));
        TestableProvider provider = provider(progressive, new AtomicBoolean());
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("deleteKeys"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("MCP_TOOL_NOT_AUTHORIZED", blocked.get("reasonCode"));
        verifyBlocked(progressive, "deleteKeys");
    }

    @Test
    void blockedRemoteToolIsBlockedAndAudited() {
        McpRuntimeOperationsPort progressive = routedProgressive(schema(true, "LOW", List.of("READ")));
        TestableProvider provider = provider(progressive, new AtomicBoolean());
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("*"), List.of("deleteKeys")))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("deleteKeys"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("MCP_TOOL_NOT_AUTHORIZED", blocked.get("reasonCode"));
        verifyBlocked(progressive, "deleteKeys");
    }

    @Test
    void highRiskToolRequiresChangePackageInsteadOfRemoteCall() {
        McpRuntimeOperationsPort progressive = routedProgressive(schema(true, "HIGH", List.of("READ")));
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("searchLogs"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
        verifyBlocked(progressive, "searchLogs");
    }

    @Test
    void mutatingToolRequiresChangePackageEvenWhenAuthorized() {
        McpRuntimeOperationsPort progressive = routedProgressive(schema(false, "MEDIUM", List.of("UPDATE")));
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("updateConfig"), List.of()))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("updateConfig"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
        verifyBlocked(progressive, "updateConfig");
    }

    @Test
    void allowedToolWithoutSafetyMetadataRequiresChangePackage() {
        McpRuntimeOperationsPort progressive = routedProgressive(Map.of("toolId", "tool-1", "toolName", "searchLogs"));
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("searchLogs"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("MCP_POLICY_MISSING", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
        verifyBlocked(progressive, "searchLogs");
    }

    @Test
    void readOnlyLowRiskToolCanReachRemoteCallback() {
        McpRuntimeOperationsPort progressive = routedProgressive(schema(true, "LOW", List.of("READ")));
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        String result = dispatcher.call(input("searchLogs"));

        assertEquals("remote-ok:searchLogs", JSON.parseObject(result).getString("rawPreview"));
        assertTrue(remoteCalled.get());
    }

    @Test
    void preApprovalReadOnlyToolDoesNotRequirePrepareAllowedFlag() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>(schema(true, "LOW", List.of("READ")));
        schema.put("prepareAllowed", false);
        McpRuntimeOperationsPort progressive = routedProgressive(schema);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        String result = dispatcher.call(input("searchLogs"));

        assertEquals("remote-ok:searchLogs", JSON.parseObject(result).getString("rawPreview"));
        assertTrue(remoteCalled.get());
    }

    @Test
    void ordinaryDispatcherCannotUseApprovedLandingContextForMutatingTool() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("toolId", "tool-1");
        schema.put("toolName", "updateConfig");
        schema.put("readOnly", false);
        schema.put("riskLevel", "HIGH");
        schema.put("allowedActions", List.of("UPDATE"));
        schema.put("policyStatus", "ACTIVE");
        schema.put("reviewStatus", "HUMAN_REVIEWED");
        schema.put("effectType", "MUTATE_TARGET_RESOURCE");
        schema.put("effectScope", "TARGET_RESOURCE_WRITE");
        schema.put("mutability", "PROD_MUTATING");
        schema.put("landAllowed", true);
        schema.put("requiresApprovedPackage", true);
        McpRuntimeOperationsPort progressive = routedProgressive(schema);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        OpsMcpServerConfig config = config(true, List.of("updateConfig"), List.of());
        config.setLandingApproved(true);
        config.setChangePackageId("cp-1");
        config.setApprovedPackageHash("hash-1");
        config.setApprovedPackageVersion(1);
        config.setInternalCaller(cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter.LANDING_INTERNAL_CALLER);
        config.setLandingRuntimeToken(cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter.LANDING_RUNTIME_TOKEN);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config)).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("updateConfig"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
    }

    @Test
    void approvedLandingContextWithoutLandAllowedPolicyStillBlocksMutatingTool() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("toolId", "tool-1");
        schema.put("toolName", "updateConfig");
        schema.put("readOnly", false);
        schema.put("riskLevel", "HIGH");
        schema.put("allowedActions", List.of("UPDATE"));
        schema.put("policyStatus", "ACTIVE");
        schema.put("reviewStatus", "HUMAN_REVIEWED");
        schema.put("effectType", "MUTATE_TARGET_RESOURCE");
        schema.put("effectScope", "TARGET_RESOURCE_WRITE");
        schema.put("mutability", "PROD_MUTATING");
        schema.put("landAllowed", false);
        schema.put("requiresApprovedPackage", true);
        McpRuntimeOperationsPort progressive = routedProgressive(schema);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        OpsMcpServerConfig config = config(true, List.of("updateConfig"), List.of());
        config.setLandingApproved(true);
        config.setChangePackageId("cp-1");
        config.setApprovedPackageHash("hash-1");
        config.setApprovedPackageVersion(1);
        config.setInternalCaller(cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter.LANDING_INTERNAL_CALLER);
        config.setLandingRuntimeToken(cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter.LANDING_RUNTIME_TOKEN);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config)).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("updateConfig"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
    }

    @Test
    void pendingReviewPolicyIsBlockedEvenIfRemoteMetadataLooksReadOnly() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>(schema(true, "LOW", List.of("READ")));
        schema.put("policyStatus", "PENDING_REVIEW");
        schema.put("reviewStatus", "SYSTEM_SUGGESTED");
        McpRuntimeOperationsPort progressive = routedProgressive(schema);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, input("searchLogs"));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("MCP_POLICY_PENDING_REVIEW", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
    }

    @Test
    void dryRunToolIsAllowedInPreApprovalWorkflowThroughUnifiedEntry() {
        Map<String, Object> schema = activePolicy(false, "MEDIUM", List.of("DRY_RUN"),
                "DRY_RUN", "VALIDATION_ENVIRONMENT", "DRY_RUN_ONLY");
        schema.put("prepareAllowed", true);
        McpRuntimeOperationsPort progressive = routedProgressive(schema);

        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        OpsMcpServerConfig config = config(true, List.of("dryRunChange"), List.of());
        config.setToolCallStage("INVESTIGATE");
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config)).get(0);

        assertEquals("remote-ok:dryRunChange", JSON.parseObject(dispatcher.call(input("dryRunChange"))).getString("rawPreview"));
        assertTrue(remoteCalled.get());
    }

    @Test
    void argumentPolicyRejectsWriteSqlForReadOnlyTool() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>(schema(true, "LOW", List.of("READ")));
        schema.put("argumentPolicy", Map.of("sqlReadOnlyOnly", true));
        McpRuntimeOperationsPort progressive = routedProgressive(schema);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        TestableProvider provider = provider(progressive, remoteCalled);
        ToolCallback dispatcher = provider.buildToolCallbacks(List.of(config(true, List.of("searchLogs"), List.of()))).get(0);

        Map<String, Object> blocked = blocked(dispatcher, JSON.toJSONString(Map.of(
                "toolName", "searchLogs",
                "arguments", Map.of("sql", "delete from orders where id=1"))));

        assertEquals("BLOCKED", blocked.get("status"));
        assertEquals("MCP_TOOL_ARGUMENT_POLICY_VIOLATION", blocked.get("reasonCode"));
        assertTrue(!remoteCalled.get());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> blocked(ToolCallback dispatcher, String input) {
        return JSON.parseObject(dispatcher.call(input), Map.class);
    }

    private TestableProvider provider(McpRuntimeOperationsPort progressive, AtomicBoolean remoteCalled) {
        McpSummaryPort summaries = mock(McpSummaryPort.class);
        when(summaries.summary(anyString())).thenReturn(Map.of("toolCount", 1));
        McpDiscoveryPort discovery = mock(McpDiscoveryPort.class);
        McpDiscoverySelectionResult selected = mock(McpDiscoverySelectionResult.class);
        when(selected.selected()).thenReturn(true);
        when(discovery.select(any(McpDiscoverySelectionRequest.class))).thenReturn(selected);
        Map<String, Object> schema = discoverySchemas.getOrDefault(progressive, Map.of());
        McpHydratedToolSchema hydratedSchema = mock(McpHydratedToolSchema.class);
        when(hydratedSchema.view()).thenReturn(schema);
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(hydratedSchema);
        ProgressiveMcpProcessManager processManager = new ProgressiveMcpProcessManager(
                summaries, discovery, progressive);
        ObjectProvider<ProgressiveMcpProcessManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(processManager);
        OpsToolExecutionPolicy policy = mock(OpsToolExecutionPolicy.class);
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        AtomicReference<OpsToolExecutionService> executionServiceRef = new AtomicReference<>();
        OpsMcpRemoteClientAdapter remoteClientAdapter = new OpsMcpRemoteClientAdapter(
                new OpsMcpClientRegistry(),
                new OpsMcpClientFactory(secretResolver, new OpsMcpTransportSecurityPolicy()));
        OpsMcpRemoteInvocationAdapter remoteInvocationAdapter = new OpsMcpRemoteInvocationAdapter(
                remoteClientAdapter,
                provider::getIfAvailable);
        OpsMcpRemoteCallPolicy remoteCallPolicy = new OpsMcpRemoteCallPolicy(
                new OpsMcpToolArgumentPolicyChecker());
        OpsMcpProgressiveRuntimeAdapter progressiveRuntimeAdapter = new OpsMcpProgressiveRuntimeAdapter(
                provider::getIfAvailable,
                OpsMcpProgressiveSettings.forTest(true, false),
                remoteCallPolicy);
        TestableProvider toolProvider = new TestableProvider(
                policy,
                remoteCalled,
                executionServiceRef,
                remoteCallPolicy,
                remoteClientAdapter,
                remoteInvocationAdapter,
                progressiveRuntimeAdapter);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsMcpToolProvider> mcpProvider = mock(ObjectProvider.class);
        when(mcpProvider.getObject()).thenReturn(toolProvider);
        when(mcpProvider.getIfAvailable()).thenReturn(toolProvider);
        OpsMcpExecutionMapper mcpMapper = new OpsMcpExecutionMapper();
        McpExecutionApplicationService mcpExecution = new McpExecutionApplicationService(
                new OpsMcpExecutionRuntimeAdapter(processManager),
                new OpsMcpExecutionRemoteAdapter(mcpProvider, mcpMapper),
                new OpsMcpExecutionRouterAdapter(new OpsToolsetRouter()),
                command -> new McpExecutionRecordedResult(
                        "result-test", "evidence-test", "preview", "a".repeat(64), false,
                        "tool-result://result-test", "b".repeat(64), command.durationMs()),
                event -> { },
                System::nanoTime);
        AtomicReference<OpsMcpServerConfig> currentRuntimeConfig = new AtomicReference<>();
        OpsProjectMcpRuntimeConfigService runtimeConfigs = mock(OpsProjectMcpRuntimeConfigService.class);
        when(runtimeConfigs.resolve(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(currentRuntimeConfig.get()));
        OpsToolExecutionMapper toolMapper = new OpsToolExecutionMapper();
        ToolExecutionApplicationService unifiedToolExecution = new ToolExecutionApplicationService(
                new OpsToolExecutionCatalogAdapter(
                        mock(OpsToolsetCatalogService.class),
                        new OpsToolsetRouter()),
                new OpsCompositeToolExecutionDispatchAdapter(
                        ToolExecutionTarget::binding,
                        List.of(new OpsMcpToolInvoker(
                                new OpsMcpToolExecutionDispatchHandler(
                                        mcpExecution,
                                        mcpMapper,
                                        runtimeConfigs)))),
                command -> new ToolExecutionRecordedResult(
                        "tool-result-test",
                        "tool-evidence-test",
                        "preview",
                        "c".repeat(64),
                        false,
                        "tool-result://tool-result-test",
                        "d".repeat(64),
                        command.durationMs()),
                (request, type, payload) -> { },
                event -> { },
                () -> "tool-call-test",
                System::nanoTime);
        OpsToolExecutionService executionService = new OpsToolExecutionService(
                unifiedToolExecution,
                toolMapper,
                mcpExecution,
                mcpMapper) {
            @Override
            public Map<String, Object> executeMcp(
                    OpsMcpServerConfig config,
                    String toolInput,
                    String actor) {
                currentRuntimeConfig.set(config);
                try {
                    return super.executeMcp(config, toolInput, actor);
                } finally {
                    currentRuntimeConfig.set(null);
                }
            }

            @Override
            public Map<String, Object> executeMcp(
                    OpsMcpServerConfig config,
                    String toolInput,
                    String actor,
                    String idempotencyKey) {
                currentRuntimeConfig.set(config);
                try {
                    return super.executeMcp(config, toolInput, actor, idempotencyKey);
                } finally {
                    currentRuntimeConfig.set(null);
                }
            }

            @Override
            public Map<String, Object> executeMcp(
                    OpsMcpServerConfig config,
                    String toolInput,
                    String actor,
                    boolean readOnlyHint) {
                currentRuntimeConfig.set(config);
                try {
                    return super.executeMcp(config, toolInput, actor, readOnlyHint);
                } finally {
                    currentRuntimeConfig.set(null);
                }
            }

            @Override
            public Map<String, Object> executeLandingMcp(
                    OpsMcpServerConfig config,
                    String toolInput,
                    String actor) {
                currentRuntimeConfig.set(config);
                try {
                    return super.executeLandingMcp(config, toolInput, actor);
                } finally {
                    currentRuntimeConfig.set(null);
                }
            }

            @Override
            public Map<String, Object> executeLandingMcp(
                    OpsMcpServerConfig config,
                    String toolInput,
                    String actor,
                    String idempotencyKey) {
                currentRuntimeConfig.set(config);
                try {
                    return super.executeLandingMcp(config, toolInput, actor, idempotencyKey);
                } finally {
                    currentRuntimeConfig.set(null);
                }
            }

            @Override
            public Map<String, Object> executeLandingMcp(
                    OpsMcpServerConfig config,
                    String toolInput,
                    String actor,
                    boolean readOnlyHint) {
                currentRuntimeConfig.set(config);
                try {
                    return super.executeLandingMcp(config, toolInput, actor, readOnlyHint);
                } finally {
                    currentRuntimeConfig.set(null);
                }
            }
        };
        executionServiceRef.set(executionService);
        return toolProvider;
    }

    private IMcpRuntimeCatalogRepository runtimeStore() {
        IMcpRuntimeCatalogRepository repository = mock(IMcpRuntimeCatalogRepository.class);
        when(repository.available()).thenReturn(true);
        return repository;
    }

    private McpRuntimeOperationsPort routedProgressive(Map<String, Object> schema) {
        McpRuntimeOperationsPort progressive = mock(McpRuntimeOperationsPort.class);
        when(progressive.runtimeExecutableTools(eq("project-1"), eq("tool-1"), any(), any(), any()))
                .thenReturn(List.of(runtimeTool("searchLogs"), runtimeTool("updateConfig"), runtimeTool("dryRunChange")));
        discoverySchemas.put(progressive, schema);
        return progressive;
    }

    private Map<String, Object> runtimeTool(String toolName) {
        return Map.of("toolName", toolName, "policyId", "policy-" + toolName, "riskLevel", "LOW", "readOnly", true);
    }

    private Map<String, Object> schema(boolean readOnly, String riskLevel, List<String> allowedActions) {
        return activePolicy(readOnly, riskLevel, allowedActions,
                readOnly ? "READ_EXTERNAL_STATE" : "MUTATE_TARGET_RESOURCE",
                readOnly ? "TARGET_RESOURCE_READ" : "TARGET_RESOURCE_WRITE",
                readOnly ? "READ_ONLY" : "PROD_MUTATING");
    }

    private Map<String, Object> activePolicy(boolean readOnly,
                                             String riskLevel,
                                             List<String> allowedActions,
                                             String effectType,
                                             String effectScope,
                                             String mutability) {
        boolean lowOrMedium = "LOW".equalsIgnoreCase(riskLevel) || "MEDIUM".equalsIgnoreCase(riskLevel);
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("toolId", "tool-1");
        schema.put("toolName", "searchLogs");
        schema.put("readOnly", readOnly);
        schema.put("riskLevel", riskLevel);
        schema.put("allowedActions", allowedActions);
        schema.put("policyStatus", "ACTIVE");
        schema.put("reviewStatus", "HUMAN_REVIEWED");
        schema.put("effectType", effectType);
        schema.put("effectScope", effectScope);
        schema.put("mutability", mutability);
        schema.put("capability", readOnly ? "READ_ONLY" : "MUTATING");
        schema.put("investigateAllowed", readOnly && lowOrMedium);
        schema.put("prepareAllowed", readOnly);
        schema.put("landAllowed", false);
        schema.put("requiresApprovedPackage", !readOnly || !lowOrMedium);
        schema.put("argumentPolicy", Map.of());
        return schema;
    }

    private OpsMcpServerConfig config(boolean progressiveManaged, List<String> allowedTools, List<String> blockedTools) {
        return OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .projectId("project-1")
                .runId("run-1")
                .agentId("agent-1")
                .nodeId("node-1")
                .mcpId("mcp-1")
                .toolId("tool-1")
                .progressiveManaged(progressiveManaged)
                .allowedTools(allowedTools)
                .blockedTools(blockedTools)
                .build();
    }

    private String input(String toolName) {
        return JSON.toJSONString(Map.of("toolName", toolName, "arguments", Map.of("query", "5xx")));
    }

    private void verifyBlocked(McpRuntimeOperationsPort progressive, String toolName) {
        verify(progressive).recordMcpCall(argThat((McpCommands.RuntimeCall command) ->
                "project-1".equals(command.projectId())
                        && "agent-1".equals(command.agentId())
                        && "node-1".equals(command.nodeId())
                        && "run-1".equals(command.runId())
                        && "tool-1".equals(command.toolId())
                        && "mcp-1".equals(command.mcpId())
                        && toolName.equals(command.toolName())
                        && "BLOCKED".equals(command.status())));
    }

    private static class TestableProvider extends OpsMcpToolProvider {

        private final AtomicBoolean remoteCalled;

        TestableProvider(OpsToolExecutionPolicy toolExecutionPolicy,
                         AtomicBoolean remoteCalled,
                         AtomicReference<OpsToolExecutionService> executionServiceRef,
                         OpsMcpRemoteCallPolicy remoteCallPolicy,
                         OpsMcpRemoteClientAdapter remoteClientAdapter,
                         OpsMcpRemoteInvocationAdapter remoteInvocationAdapter,
                         OpsMcpProgressiveRuntimeAdapter progressiveRuntimeAdapter) {
            super(new OpsMcpCallbackPolicyAdapter(toolExecutionPolicy),
                    new OpsProgressiveMcpCallbackAdapter(executionServiceRef::get),
                    remoteCallPolicy,
                    remoteClientAdapter,
                    remoteInvocationAdapter,
                    progressiveRuntimeAdapter);
            this.remoteCalled = remoteCalled;
        }

        @Override
        protected String invokeRemoteMcpTool(OpsMcpServerConfig config, String remoteToolName, String remoteArgs) {
            remoteCalled.set(true);
            return "remote-ok:" + remoteToolName;
        }
    }
}
