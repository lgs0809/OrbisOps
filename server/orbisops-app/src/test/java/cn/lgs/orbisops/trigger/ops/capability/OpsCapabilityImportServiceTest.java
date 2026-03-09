package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.mcp.McpDiscoveryPort;
import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpRuntimeOperationsPort;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.McpSummaryPort;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectExternalMcpApplicationService;
import cn.lgs.orbisops.application.skill.SkillCatalogMutationUseCase;
import cn.lgs.orbisops.application.skill.SkillCatalogPort;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillCatalogSnapshot;
import cn.lgs.orbisops.application.skill.SkillCatalogWriteOutcome;
import cn.lgs.orbisops.application.skill.SkillEvolutionPublishUseCase;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.application.skill.SkillPackageQueryService;
import cn.lgs.orbisops.application.skill.SkillRollbackUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpsCapabilityImportServiceTest {

    private SkillManagementUseCase management(SkillCatalogPort catalog) {
        return management(catalog, mock(SkillCatalogMutationUseCase.class));
    }

    private SkillManagementUseCase management(SkillCatalogPort catalog,
                                              SkillCatalogMutationUseCase mutationUseCase) {
        return new SkillManagementUseCase(
                catalog,
                mutationUseCase,
                mock(SkillRollbackUseCase.class),
                mock(SkillEvolutionPublishUseCase.class),
                mock(SkillPackageQueryService.class));
    }

    private SkillCatalogQueryService query(SkillCatalogPort catalog) {
        return new SkillCatalogQueryService(catalog, mock(SkillPackageQueryService.class));
    }

    private ProgressiveMcpProcessManager progressive(McpDiscoveryPort discovery) {
        return new ProgressiveMcpProcessManager(
                mock(McpSummaryPort.class), discovery, mock(McpRuntimeOperationsPort.class));
    }

    @Test
    void importedSkillIsPausedManualOnlyAndKeepsPackageContent() {
        SkillCatalogPort skills = mock(SkillCatalogPort.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityArtifactFetcher fetcher = mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urls = mock(OpsCapabilityImportUrlPolicy.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        when(projectAccess.role("p1", "alice", "u1", false)).thenReturn(ProjectRole.OWNER);
        String markdown = "---\nname: slow-sql\n---\n# Slow SQL\nRead evidence first.";
        when(fetcher.fetch(eq("https://example.com/SKILL.md"), anyLong())).thenReturn(
                new OpsCapabilityArtifactFetcher.FetchedArtifact(URI.create("https://example.com/SKILL.md"),
                        markdown.getBytes(StandardCharsets.UTF_8), "text/markdown"));
        SkillCatalogMutationUseCase mutation = mock(SkillCatalogMutationUseCase.class);
        when(mutation.createProject(eq("p1"), anyMap(), eq("u1"))).thenAnswer(invocation -> {
            Map<String, Object> command = invocation.getArgument(1);
            return createdOutcome("p1", String.valueOf(command.get("skillId")));
        });
        when(skills.getProjectEntry(eq("p1"), anyString())).thenAnswer(invocation ->
                skillSnapshot("p1", invocation.getArgument(1, String.class)));
        OpsCapabilityImportService service = new OpsCapabilityImportService(
                management(skills, mutation), query(skills), projectAccess, fetcher, urls, audit);
        Map<String, Object> result = service.importSkill(
                "p1",
                "https://example.com/SKILL.md",
                Map.of(
                        "capabilityName", "Slow SQL",
                        "whenToUse", java.util.List.of("排查慢 SQL"),
                        "whenNotToUse", java.util.List.of("数据库结构设计")),
                "alice", "u1", false);

        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(mutation).createProject(eq("p1"), request.capture(), eq("u1"));
        assertEquals("PAUSED", request.getValue().get("status"));
        assertEquals("MANUAL_ONLY", request.getValue().get("updateMode"));
        assertEquals(false, request.getValue().get("autoUpdateEnabled"));
        assertEquals(markdown, request.getValue().get("content"));
        assertEquals("IMPORTED_PAUSED", result.get("status"));
    }

    @Test
    void importedSkillRequestsMissingRoutingBoundaryBeforeCreation() {
        SkillCatalogPort skills = mock(SkillCatalogPort.class);
        AuthorizeProjectAccessUseCase projectAccess =
                mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityArtifactFetcher fetcher =
                mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urls =
                mock(OpsCapabilityImportUrlPolicy.class);
        OpsConfigAuditService audit =
                mock(OpsConfigAuditService.class);
        when(projectAccess.role("p1", "alice", "u1", false))
                .thenReturn(ProjectRole.OWNER);
        when(fetcher.fetch(
                eq("https://example.com/SKILL.md"),
                anyLong())).thenReturn(
                new OpsCapabilityArtifactFetcher.FetchedArtifact(
                        URI.create("https://example.com/SKILL.md"),
                        "# Skill".getBytes(StandardCharsets.UTF_8),
                        "text/markdown"));
        SkillCatalogMutationUseCase mutation =
                mock(SkillCatalogMutationUseCase.class);
        OpsCapabilityImportService service =
                new OpsCapabilityImportService(
                        management(skills, mutation),
                        query(skills),
                        projectAccess,
                        fetcher,
                        urls,
                        audit);

        Map<String, Object> result = service.importSkill(
                "p1",
                "https://example.com/SKILL.md",
                Map.of(),
                "alice", "u1", false);

        assertEquals("INPUT_REQUIRED", result.get("status"));
        assertEquals(
                java.util.List.of("whenToUse", "whenNotToUse"),
                result.get("requiredFields"));
        verifyNoInteractions(mutation);
    }

    @Test
    void importedMcpIsRegisteredPendingReviewWithoutUserDeclaredActions() {
        SkillCatalogPort skills = mock(SkillCatalogPort.class);
        AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityArtifactFetcher fetcher = mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urls = mock(OpsCapabilityImportUrlPolicy.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsMcpToolProvider provider = mock(OpsMcpToolProvider.class);
        ProjectExternalMcpApplicationService externalMcpService =
                mock(ProjectExternalMcpApplicationService.class);
        OpsProjectMcpRuntimeConfigService runtimeConfigService =
                mock(OpsProjectMcpRuntimeConfigService.class);
        McpDiscoveryPort discovery = mock(McpDiscoveryPort.class);
        URI uri = URI.create("https://mcp.example.com/mcp");
        OpsMcpServerConfig server = OpsMcpServerConfig.builder().mcpId("logs-123").projectId("p1").build();
        Map<String, Object> definition = Map.of("toolName", "search_logs", "description", "Search logs",
                "inputSchema", Map.of("type", "object"));
        when(urls.validate(uri.toString())).thenReturn(uri);
        when(projectAccess.role("p1", "alice", "u1", false)).thenReturn(ProjectRole.OWNER);
        when(externalMcpService.register(eq("p1"), anyString(), eq("日志平台"), eq(uri.toString()),
                eq("streamable-http"), eq("${env:MCP_TOKEN}"), eq("BEARER")))
                .thenReturn(Map.of("mcpId", "logs-123", "status", "PENDING_REVIEW", "allowedActions", java.util.List.of()));
        when(runtimeConfigService.resolveForDiscovery("p1", "logs-123"))
                .thenReturn(java.util.Optional.of(server));
        when(provider.inspectRemoteToolDefinitions(server)).thenReturn(java.util.List.of(definition));
        McpHydratedToolSchema hydratedSchema = mock(McpHydratedToolSchema.class);
        when(hydratedSchema.view()).thenReturn(Map.of(
                "remoteToolName", "search_logs", "schemaHash", "schema-1",
                "policyId", "policy-1", "policyStatus", "PENDING_REVIEW", "reviewStatus", "AI_SUGGESTED"));
        when(discovery.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(hydratedSchema);
        when(externalMcpService.recordDiscovery(eq("p1"), eq("logs-123"), anyList(), eq("DISCOVERED"), eq("")))
                .thenReturn(Map.of("mcpId", "logs-123", "status", "PENDING_REVIEW", "connectionStatus", "DISCOVERED"));
        OpsCapabilityImportService service = new OpsCapabilityImportService(
                management(skills), query(skills), projectAccess, fetcher, urls, audit,
                externalMcpService, runtimeConfigService, provider,
                progressive(discovery));
        Map<String, Object> result = service.importMcp(
                "p1",
                uri.toString(),
                Map.of("capabilityName", "日志平台", "credentialRef", "${env:MCP_TOKEN}"),
                "alice", "u1", false);

        assertEquals("DISCOVERED_PENDING_REVIEW", result.get("status"));
        assertFalse(((java.util.List<?>) result.get("policySuggestions")).isEmpty());
        verifyNoInteractions(fetcher);
        verify(externalMcpService).register(eq("p1"), anyString(), eq("日志平台"), eq(uri.toString()),
                eq("streamable-http"), eq("${env:MCP_TOKEN}"), eq("BEARER"));
        verify(provider).inspectRemoteToolDefinitions(server);
        verify(discovery).hydrateSchema(argThat((McpSchemaHydrationRequest command) ->
                "p1".equals(command.projectId())
                        && "logs-123".equals(command.toolId())
                        && "search_logs".equals(command.remoteToolName())
                        && "Search logs".equals(
                                command.authoritativeDefinition().description())
                        && command.authoritativeDefinition().hydrated()));
        verify(externalMcpService).recordDiscovery(
                eq("p1"), eq("logs-123"), anyList(), eq("DISCOVERED"), eq(""));
    }

    @Test
    void registeredMcpReportsRecoverableDiscoveryFailureWhenRuntimeIsUnavailable() {
        SkillCatalogPort skills = mock(SkillCatalogPort.class);
        AuthorizeProjectAccessUseCase projectAccess =
                mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityArtifactFetcher fetcher =
                mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urls =
                mock(OpsCapabilityImportUrlPolicy.class);
        OpsConfigAuditService audit =
                mock(OpsConfigAuditService.class);
        ProjectExternalMcpApplicationService externalMcpService =
                mock(ProjectExternalMcpApplicationService.class);
        URI uri = URI.create("https://mcp.example.com/mcp");
        when(urls.validate(uri.toString())).thenReturn(uri);
        when(projectAccess.role("p1", "alice", "u1", false))
                .thenReturn(ProjectRole.OWNER);
        when(externalMcpService.register(
                eq("p1"),
                anyString(),
                eq("日志平台"),
                eq(uri.toString()),
                eq("streamable-http"),
                eq("${env:MCP_TOKEN}"),
                eq("BEARER")))
                .thenReturn(Map.of(
                        "mcpId", "logs-123",
                        "status", "PENDING_REVIEW"));
        when(externalMcpService.recordDiscovery(
                eq("p1"),
                eq("logs-123"),
                eq(java.util.List.of()),
                eq("DISCOVERY_FAILED"),
                eq("MCP_DISCOVERY_SERVICE_NOT_CONFIGURED")))
                .thenReturn(Map.of(
                        "mcpId", "logs-123",
                        "status", "PENDING_REVIEW",
                        "connectionStatus", "DISCOVERY_FAILED"));
        OpsCapabilityImportService service = new OpsCapabilityImportService(
                management(skills),
                query(skills),
                projectAccess,
                fetcher,
                urls,
                audit,
                externalMcpService,
                null,
                null,
                null);

        Map<String, Object> result = service.importMcp(
                "p1",
                uri.toString(),
                Map.of(
                        "capabilityName", "日志平台",
                        "credentialRef", "${env:MCP_TOKEN}"),
                "alice", "u1", false);

        assertEquals("DISCOVERY_FAILED", result.get("status"));
        assertEquals(
                "MCP_DISCOVERY_SERVICE_NOT_CONFIGURED",
                result.get("discoveryError"));
        verify(externalMcpService).recordDiscovery(
                eq("p1"),
                eq("logs-123"),
                eq(java.util.List.of()),
                eq("DISCOVERY_FAILED"),
                eq("MCP_DISCOVERY_SERVICE_NOT_CONFIGURED"));
        verify(audit).recordRuntimeEvent(
                eq("p1"),
                eq(""),
                eq("u1"),
                eq("capability-import"),
                eq("MCP_REGISTERED"),
                eq("logs-123"),
                eq("HIGH"),
                eq("FAILED"),
                anyMap());
    }

    private SkillCatalogSnapshot skillSnapshot(String projectId, String skillId) {
        return SkillCatalogSnapshot.fromView(Map.ofEntries(
                Map.entry("skillId", skillId),
                Map.entry("projectId", projectId),
                Map.entry("scope", "PROJECT"),
                Map.entry("name", skillId),
                Map.entry("description", skillId),
                Map.entry("version", 1),
                Map.entry("currentVersion", 1),
                Map.entry("skillHash", "hash-" + skillId),
                Map.entry("currentSkillHash", "hash-" + skillId),
                Map.entry("status", "PAUSED"),
                Map.entry("updateMode", "MANUAL_ONLY"),
                Map.entry("content", "# " + skillId)));
    }

    private SkillCatalogWriteOutcome createdOutcome(String projectId, String skillId) {
        SkillPackageVersion version = new SkillPackageVersion(
                0L, new SkillPackageKey("PROJECT", projectId, skillId, 1),
                "hash-1", 0, "", "", "", "", "MANUAL_CREATE",
                "content", "IMPORTED", "alice", "", "package-1",
                "{}", "{}", "SKILL.md", 1, 7L, null);
        return new SkillCatalogWriteOutcome(true, null, version);
    }
}
