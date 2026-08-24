package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeMcpResolverTest {

    @Test
    void agentDefinitionCannotElevateRuntimeStage() {
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        ToolCallback callback = mock(ToolCallback.class);
        when(toolProvider.buildToolCallbacks(anyList())).thenReturn(List.of(callback));
        OpsRuntimeMcpResolver resolver = resolver(toolProvider, null, null, null, null);
        OpsMcpServerConfig server = OpsMcpServerConfig.builder()
                .name("logs")
                .transport("stdio")
                .build();
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .node(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .config(Map.of("phase", "PREPARE"))
                        .build())
                .request(OpsAgentChatRequest.builder()
                        .projectId("project-1")
                        .runId("run-1")
                        .metadata(new LinkedHashMap<>(Map.of(
                                OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-1",
                                OpsWorkSessionClaimMetadata.LEASE_TOKEN, "lease-secret",
                                OpsWorkSessionClaimMetadata.FENCING_TOKEN, 7L,
                                OpsWorkSessionClaimMetadata.STATE_VERSION, 3L,
                                OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, "manifest-hash",
                                "unrelated", "must-not-propagate")))
                        .build())
                .projectId("project-1")
                .mcpServers(new ArrayList<>(List.of(server)))
                .build();

        resolver.resolve(context);

        assertEquals("project-1", server.getProjectId());
        assertEquals("run-1", server.getRunId());
        assertEquals("agent-1", server.getAgentId());
        assertEquals("node-1", server.getNodeId());
        assertEquals(Map.of(
                OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-1",
                OpsWorkSessionClaimMetadata.LEASE_TOKEN, "lease-secret",
                OpsWorkSessionClaimMetadata.FENCING_TOKEN, 7L,
                OpsWorkSessionClaimMetadata.STATE_VERSION, 3L,
                OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, "manifest-hash"), server.getWorkSessionClaim());
        assertEquals(OpsToolCallStage.INVESTIGATE.name(), server.getToolCallStage());
        assertEquals(List.of(callback), context.getTools());
    }

    @Test
    void agentAuthorityControlsReadonlyTestWriteAndProductionProposalVisibility() {
        OpsRuntimeMcpResolver resolver = resolver(
                emptyToolProvider(), null, null, null, null);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(1)
                .build();
        OpsMcpServerConfig testWrite = governedServer(
                "test-write", "test", false, "INVESTIGATE,PREPARE");

        OpsRuntimeResourceContext diagnosticContext = governedContext(
                definition, TriggerSource.INSPECTION, testWrite);
        resolver.resolve(diagnosticContext);
        assertTrue(diagnosticContext.getMcpServers().isEmpty());

        OpsRuntimeResourceContext chatObserveOnly = governedContext(
                definition,
                TriggerSource.CHAT,
                governedServer("test-write", "test", false, "INVESTIGATE,PREPARE"));
        resolver.resolve(chatObserveOnly);
        assertTrue(chatObserveOnly.getMcpServers().isEmpty());

        OpsRuntimeResourceContext prepareContext = governedContext(
                definition,
                TriggerSource.CHAT,
                "PREPARE_CHANGE",
                governedServer("test-write", "test", false, "INVESTIGATE,PREPARE"));
        resolver.resolve(prepareContext);
        assertEquals(1, prepareContext.getMcpServers().size());
        assertEquals("PREPARE", prepareContext.getMcpServers().get(0).getToolCallStage());
        assertEquals("PREPARE_CHANGE", prepareContext.getMcpServers().get(0).getRuntimeAuthority());

        OpsRuntimeResourceContext productionWrite = governedContext(
                definition,
                TriggerSource.CHAT,
                "PREPARE_CHANGE",
                governedServer("prod-write", "prod", false, "LANDING"));
        resolver.resolve(productionWrite);
        assertEquals(1, productionWrite.getMcpServers().size());
        assertEquals("PREPARE", productionWrite.getMcpServers().get(0).getToolCallStage());
        assertEquals("PREPARE_CHANGE", productionWrite.getMcpServers().get(0).getRuntimeAuthority());
    }

    @Test
    void incompleteGovernanceMetadataAlwaysFailsClosed() {
        OpsRuntimeMcpResolver resolver = resolver(
                emptyToolProvider(), null, null, null, null);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(1)
                .definitionHash("definition-hash")
                .instruction("Investigate only.")
                .build();
        List<OpsMcpServerConfig> invalid = List.of(
                incompleteServer("missing-all", Map.of()),
                incompleteServer("missing-stages", Map.of(
                        "resourceEnvironment", "prod",
                        "readOnly", "true")),
                incompleteServer("missing-readonly", Map.of(
                        "resourceEnvironment", "prod",
                        "allowedStages", "INVESTIGATE")),
                incompleteServer("unknown-environment", Map.of(
                        "resourceEnvironment", "unknown",
                        "allowedStages", "INVESTIGATE",
                        "readOnly", "true")));

        for (OpsMcpServerConfig server : invalid) {
            OpsRuntimeResourceContext context = governedContext(
                    definition, TriggerSource.INSPECTION, server);
            resolver.resolve(context);
            assertTrue(context.getMcpServers().isEmpty(), server.getName());
            assertTrue(context.getEvents().stream().anyMatch(event ->
                    "MCP_CAPABILITY_BLOCKED".equals(event.getEventType())), server.getName());
        }
    }

    @Test
    void landingMayAssembleProjectProdMcpWithoutPerToolApprovalAcl() {
        OpsRuntimeMcpResolver resolver = resolver(
                emptyToolProvider(), null, null, null, null);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(1)
                .definitionHash("definition-hash")
                .instruction("Landing preflight only.")
                .modelId("model-1")
                .build();
        var approved = new cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot(
                "pkg-1",
                1,
                "package-hash",
                "project-1",
                "prod",
                "artifact-hash",
                java.time.Instant.now().plusSeconds(60));
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY, approved);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("project-1")
                .runId("run-landing-1")
                .sessionId("session-landing-1")
                .metadata(metadata)
                .build();
        var executionContext = new OpsAgentRunExecutionContextFactory()
                .bindServerContext(request, definition);
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(definition)
                .request(request)
                .executionContext(executionContext)
                .projectId("project-1")
                .mcpServers(new ArrayList<>(List.of(
                        governedServer("deploy", "prod", false, "LANDING"))))
                .events(new ArrayList<>())
                .build();

        resolver.resolve(context);

        assertEquals(1, context.getMcpServers().size());
        assertTrue(context.getTools().isEmpty());
    }

    @Test
    void landingDecoratesRuntimeConfigWithFrozenOperationBindings() {
        OpsMcpToolProvider toolProvider = emptyToolProvider();
        LandingOperationJournalApplicationService journal = mock(LandingOperationJournalApplicationService.class);
        when(journal.operationExecutionBindings("run-landing-1")).thenReturn(List.of(
                new LandingOperationExecutionBinding(
                        "operation-1", "execution-1", "mcp.deploy", "restart_service", "service://demo")));
        OpsRuntimeMcpResolver resolver = new OpsRuntimeMcpResolver(
                toolProvider,
                new OpsMcpRuntimeConfigSourceChain(
                        List.of(new OpsMcpRuntimeConfigSource() {
                            @Override
                            public String sourceId() { return "TEST"; }

                            @Override
                            public int order() { return 1; }

                            @Override
                            public OpsMcpRuntimeConfigSourceResult resolve(
                                    OpsMcpRuntimeConfigRequest request) {
                                return OpsMcpRuntimeConfigSourceResult.miss("TEST_NO_DYNAMIC_CONFIG");
                            }
                        }),
                        OpsMcpRuntimeConfigResolutionObserver.noop()),
                () -> null,
                null,
                () -> journal);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(1)
                .build();
        var approved = new cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot(
                "pkg-1", 1, "package-hash", "project-1", "prod", "artifact-hash",
                java.time.Instant.now().plusSeconds(60));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("project-1")
                .runId("run-landing-1")
                .metadata(new LinkedHashMap<>(Map.of(
                        OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY, approved)))
                .build();
        var executionContext = new OpsAgentRunExecutionContextFactory()
                .bindServerContext(request, definition);
        OpsMcpServerConfig server = governedServer("deploy", "prod", false, "LANDING");
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(definition)
                .request(request)
                .executionContext(executionContext)
                .projectId("project-1")
                .mcpServers(new ArrayList<>(List.of(server)))
                .events(new ArrayList<>())
                .build();

        resolver.resolve(context);

        assertEquals(List.of(new LandingOperationExecutionBinding(
                "operation-1", "execution-1", "mcp.deploy", "restart_service", "service://demo")),
                server.getLandingOperationBindings());
    }

    @Test
    void projectRuntimeDescriptorMustWinBeforeLegacyAndSourceFallbacks() {
        OpsMcpToolProvider toolProvider = emptyToolProvider();
        OpsProjectMcpRuntimeConfigService runtimeConfig = mock(OpsProjectMcpRuntimeConfigService.class);
        McpClientCatalogPort legacyRepository = mock(McpClientCatalogPort.class);
        OpsSourceRepositoryService sourceRepository = mock(OpsSourceRepositoryService.class);
        OpsMcpServerConfig configured = governedServer(
                "project-runtime", "prod", true, "INVESTIGATE");
        when(runtimeConfig.resolve("project-1", "mcp-1")).thenReturn(Optional.of(configured));
        OpsRuntimeMcpResolver resolver = resolver(
                toolProvider, runtimeConfig, legacyRepository, null, sourceRepository);
        OpsRuntimeResourceContext context = context("project-1", "mcp-1");

        resolver.resolve(context);

        assertEquals(List.of(configured), context.getMcpServers());
        verify(legacyRepository, never()).findByMcpId("mcp-1");
        verify(sourceRepository, never()).resolveMcpServer("project-1", "mcp-1");
        assertResolution(context, OpsProjectMcpRuntimeConfigSource.SOURCE_ID, false);
    }

    @Test
    void legacyConfigMustFailClosedWhenProjectAuthorizationIsUnavailable() {
        OpsMcpToolProvider toolProvider = emptyToolProvider();
        McpClientCatalogPort legacyRepository = mock(McpClientCatalogPort.class);
        OpsSourceRepositoryService sourceRepository = mock(OpsSourceRepositoryService.class);
        when(legacyRepository.findByMcpId("legacy-1")).thenReturn(legacyRecord("legacy-1"));
        OpsRuntimeMcpResolver resolver = resolver(
                toolProvider, null, legacyRepository, null, sourceRepository);
        OpsRuntimeResourceContext context = context("project-1", "legacy-1");

        resolver.resolve(context);

        assertTrue(context.getMcpServers().isEmpty());
        assertTrue(context.getEvents().stream().anyMatch(event ->
                "RESOURCE_WARN".equals(event.getEventType())
                        && event.getSummary().contains("PROJECT_AUTHORIZATION_UNAVAILABLE")));
        verify(sourceRepository, never()).resolveMcpServer("project-1", "legacy-1");
        assertResolution(context, OpsLegacyMcpRuntimeConfigSource.SOURCE_ID, true);
    }

    @Test
    void sourceRepositoryMustRemainLastCompatibilityFallback() {
        OpsMcpToolProvider toolProvider = emptyToolProvider();
        McpClientCatalogPort legacyRepository = mock(McpClientCatalogPort.class);
        OpsSourceRepositoryService sourceRepository = mock(OpsSourceRepositoryService.class);
        OpsMcpServerConfig sourceConfig = governedServer(
                "source-mcp", "external", true, "INVESTIGATE");
        sourceConfig.setTransport("streamable-http");
        sourceConfig.setUrl("https://example.invalid/mcp");
        when(legacyRepository.findByMcpId("source-1")).thenReturn(null);
        when(sourceRepository.resolveMcpServer("project-1", "source-1"))
                .thenReturn(Optional.of(sourceConfig));
        OpsRuntimeMcpResolver resolver = resolver(
                toolProvider, null, legacyRepository, null, sourceRepository);
        OpsRuntimeResourceContext context = context("project-1", "source-1");

        resolver.resolve(context);

        assertSame(sourceConfig, context.getMcpServers().get(0));
        assertEquals("project-1", sourceConfig.getProjectId());
        OpsRuntimeEvent event = resolutionEvent(context);
        assertEquals(OpsSourceRepositoryMcpRuntimeConfigSource.SOURCE_ID,
                event.getPayload().get("sourceId"));
        assertEquals(true, event.getPayload().get("fallback"));
        assertTrue(String.valueOf(event.getPayload().get("fallbackReason"))
                .contains(OpsLegacyMcpRuntimeConfigSource.SOURCE_ID));
    }

    @Test
    void legacyMatchMustBeObservableForRuntimeMigration() {
        McpClientCatalogPort legacyRepository = mock(McpClientCatalogPort.class);
        ProjectMcpAuthorizationApplicationService authorization =
                mock(ProjectMcpAuthorizationApplicationService.class);
        when(legacyRepository.findByMcpId("legacy-1")).thenReturn(legacyRecord("legacy-1"));
        when(authorization.allows("project-1", "legacy-1")).thenReturn(true);
        OpsMcpRuntimeConfigResolutionTelemetry telemetry =
                new OpsMcpRuntimeConfigResolutionTelemetry((MeterRegistry) null);
        OpsRuntimeMcpResolver resolver = resolver(
                emptyToolProvider(), null, legacyRepository, authorization, null, telemetry);
        OpsRuntimeResourceContext context = context("project-1", "legacy-1");

        resolver.resolve(context);

        assertEquals(1L, telemetry.legacyFallbackCount());
        assertResolution(context, OpsLegacyMcpRuntimeConfigSource.SOURCE_ID, true);
    }

    @Test
    void projectCapabilityInheritanceMustUseSameAuthorizationCatalog() {
        ProjectMcpAuthorizationApplicationService authorization =
                mock(ProjectMcpAuthorizationApplicationService.class);
        when(authorization.enabledIds("project-1")).thenReturn(List.of("mcp-a", "mcp-b"));
        OpsRuntimeMcpResolver resolver = resolver(
                emptyToolProvider(), null, null, authorization, null);

        assertEquals(List.of("mcp-a", "mcp-b"),
                resolver.enabledProjectMcpIds("project-1"));
    }

    @Test
    void legacyMapperMustPreserveStdioAndHttpTransportContracts() {
        OpsLegacyMcpConfigMapper mapper = new OpsLegacyMcpConfigMapper();
        OpsMcpServerConfig stdio = mapper.map(new McpClientDefinition(
                null,
                "legacy-stdio",
                "legacy",
                "stdio",
                """
                        {
                          "legacy": {
                            "command": "npx",
                            "args": ["-y", "server"],
                            "env": {"TOKEN": "secret-ref"}
                          },
                          "allowedTools": ["query"]
                        }
                        """,
                12,
                1,
                null,
                null));
        OpsMcpServerConfig http = mapper.map(new McpClientDefinition(
                null,
                "legacy-http",
                "http",
                "streamable-http",
                """
                        {
                          "baseUri": "https://example.invalid/",
                          "endpoint": "mcp",
                          "headers": {"Authorization": "secret-ref"}
                        }
                        """,
                null,
                1,
                null,
                null));

        assertEquals("npx", stdio.getCommand());
        assertEquals(List.of("-y", "server"), stdio.getArgs());
        assertEquals("secret-ref", stdio.getEnv().get("TOKEN"));
        assertEquals(List.of("query"), stdio.getAllowedTools());
        assertEquals(12, stdio.getTimeoutSeconds());
        assertEquals("https://example.invalid/mcp", http.getUrl());
        assertEquals("secret-ref", http.getHeaders().get("Authorization"));
    }

    private McpClientDefinition legacyRecord(String mcpId) {
        return new McpClientDefinition(
                null,
                mcpId,
                "legacy",
                "stdio",
                "{\"legacy\":{\"command\":\"npx\"}}",
                null,
                1,
                null,
                null);
    }

    private void assertResolution(
            OpsRuntimeResourceContext context,
            String sourceId,
            boolean fallback) {
        OpsRuntimeEvent event = resolutionEvent(context);
        assertEquals(sourceId, event.getPayload().get("sourceId"));
        assertEquals(fallback, event.getPayload().get("fallback"));
    }

    private OpsRuntimeEvent resolutionEvent(OpsRuntimeResourceContext context) {
        return context.getEvents().stream()
                .filter(event -> "MCP_CONFIG_RESOLVED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
    }

    private OpsMcpServerConfig incompleteServer(
            String mcpId,
            Map<String, String> capabilities) {
        return OpsMcpServerConfig.builder()
                .name(mcpId)
                .mcpId(mcpId)
                .toolId(mcpId)
                .transport("stdio")
                .toolCapabilities(capabilities)
                .build();
    }

    private OpsMcpServerConfig governedServer(
            String mcpId,
            String environment,
            boolean readOnly,
            String allowedStages) {
        return OpsMcpServerConfig.builder()
                .name(mcpId)
                .mcpId(mcpId)
                .toolId(mcpId)
                .transport("stdio")
                .toolCapabilities(Map.of(
                        "resourceEnvironment", environment,
                        "permissionProfile", readOnly ? "DATA_READONLY" : "TEST_FULL",
                        "allowedStages", allowedStages,
                        "readOnly", String.valueOf(readOnly),
                        "riskLevel", "HIGH",
                        "effectCeiling", readOnly ? "READ_ONLY" : "WRITE"))
                .build();
    }

    private OpsRuntimeResourceContext governedContext(
            OpsAgentDefinition definition,
            TriggerSource triggerSource,
            OpsMcpServerConfig server) {
        return governedContext(definition, triggerSource, "OBSERVE_ONLY", server);
    }

    private OpsRuntimeResourceContext governedContext(
            OpsAgentDefinition definition,
            TriggerSource triggerSource,
            String authority,
            OpsMcpServerConfig server) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_trustedTriggerSource", triggerSource);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("project-1")
                .runId("run-1")
                .sessionId("session-1")
                .metadata(metadata)
                .build();
        var executionContext = new OpsAgentRunExecutionContextFactory()
                .bindServerContext(request, definition);
        return OpsRuntimeResourceContext.builder()
                .definition(definition)
                .node(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .type("AGENT")
                        .config(Map.of("authority", authority))
                        .build())
                .request(request)
                .executionContext(executionContext)
                .projectId("project-1")
                .mcpServers(new ArrayList<>(List.of(server)))
                .events(new ArrayList<>())
                .build();
    }

    private OpsRuntimeResourceContext context(String projectId, String mcpId) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .request(OpsAgentChatRequest.builder()
                        .projectId(projectId)
                        .runId("run-1")
                        .build())
                .projectId(projectId)
                .mcpIds(new LinkedHashSet<>(List.of(mcpId)))
                .events(new ArrayList<>())
                .build();
    }

    private OpsMcpToolProvider emptyToolProvider() {
        OpsMcpToolProvider provider = mock(OpsMcpToolProvider.class);
        when(provider.buildToolCallbacks(anyList())).thenReturn(List.of());
        return provider;
    }

    private OpsRuntimeMcpResolver resolver(
            OpsMcpToolProvider toolProvider,
            OpsProjectMcpRuntimeConfigService runtimeConfig,
            McpClientCatalogPort legacyRepository,
            ProjectMcpAuthorizationApplicationService authorization,
            OpsSourceRepositoryService sourceRepository) {
        return resolver(
                toolProvider, runtimeConfig, legacyRepository, authorization, sourceRepository,
                OpsMcpRuntimeConfigResolutionObserver.noop());
    }

    private OpsRuntimeMcpResolver resolver(
            OpsMcpToolProvider toolProvider,
            OpsProjectMcpRuntimeConfigService runtimeConfig,
            McpClientCatalogPort legacyRepository,
            ProjectMcpAuthorizationApplicationService authorization,
            OpsSourceRepositoryService sourceRepository,
            OpsMcpRuntimeConfigResolutionObserver observer) {
        OpsLegacyMcpConfigMapper mapper = new OpsLegacyMcpConfigMapper();
        OpsMcpRuntimeConfigSourceChain chain = new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        new OpsProjectMcpRuntimeConfigSource(provider(runtimeConfig)),
                        new OpsLegacyMcpRuntimeConfigSource(
                                provider(legacyRepository), provider(authorization), mapper),
                        new OpsSourceRepositoryMcpRuntimeConfigSource(provider(sourceRepository))),
                observer);
        return new OpsRuntimeMcpResolver(toolProvider, chain, () -> authorization);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
