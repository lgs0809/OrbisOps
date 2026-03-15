package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageProjectWorkspaceUseCaseTest {

    @Test
    void createProjectOverridesActorAcrossCreateAndDefaultAgentWriteback() {
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ProjectDefaultAgentPort defaultAgentPort = mock(ProjectDefaultAgentPort.class);
        ProjectAuditPort auditPort = mock(ProjectAuditPort.class);
        ProjectDefinitionApplicationService definitionService =
                mock(ProjectDefinitionApplicationService.class);
        ManageProjectWorkspaceUseCase useCase =
                new ManageProjectWorkspaceUseCase(
                        workspacePort, defaultAgentPort, auditPort,
                        mock(ProjectMemberApplicationService.class), definitionService,
                        mock(ProjectResourceApplicationService.class),
                        mock(ProjectMcpGenerationApplicationService.class),
                        mock(ProjectMcpManagementApplicationService.class),
                        mock(ProjectMcpCatalogApplicationService.class));
        ProjectDefinition created = project("");
        ProjectDefinition updated = project("agent-1");
        when(definitionService.createDefinition(anyMap())).thenReturn(created);
        when(defaultAgentPort.ensure("project-1", "Project One"))
                .thenReturn(new ProjectDefaultAgentResult("agent-1"));
        when(definitionService.assignDefaultAgent("project-1", "agent-1")).thenReturn(updated);
        when(workspacePort.detail("project-1")).thenReturn(Map.of(
                "projectId", "project-1",
                "name", "Project One",
                "defaultAgentId", "agent-1"));

        useCase.createProject(Map.of(
                "projectId", "project-1",
                "name", "Project One",
                "actor", "forged-user"), "alice");

        ArgumentCaptor<Map<String, Object>> createCommand = mapCaptor();
        verify(definitionService).createDefinition(createCommand.capture());
        assertEquals("alice", createCommand.getValue().get("actor"));
        verify(definitionService).assignDefaultAgent("project-1", "agent-1");
        verify(workspacePort).materializeDefinition(created);
        verify(workspacePort).materializeDefinition(updated);
        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq("project-1"),
                eq("project"),
                eq("create"),
                eq("project-1"),
                org.mockito.ArgumentMatchers.isNull(),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
    }

    @Test
    void addResourceOverridesUntrustedActorAndAuditsPrincipal() {
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ProjectDefaultAgentPort defaultAgentPort = mock(ProjectDefaultAgentPort.class);
        ProjectAuditPort auditPort = mock(ProjectAuditPort.class);
        ProjectResourceApplicationService resourceService =
                mock(ProjectResourceApplicationService.class);
        ManageProjectWorkspaceUseCase useCase =
                new ManageProjectWorkspaceUseCase(
                        workspacePort, defaultAgentPort, auditPort,
                        mock(ProjectMemberApplicationService.class),
                        mock(ProjectDefinitionApplicationService.class), resourceService,
                        mock(ProjectMcpGenerationApplicationService.class),
                        mock(ProjectMcpManagementApplicationService.class),
                        mock(ProjectMcpCatalogApplicationService.class));
        ProjectResourceDefinition resource = resource("resource-1");
        when(resourceService.createResource(anyMap())).thenReturn(resource);
        when(workspacePort.detail("project-1")).thenReturn(Map.of(
                "projectId", "project-1",
                "resources", List.of(Map.of("resourceId", "resource-1"))));

        useCase.addResource(Map.of(
                "projectId", "project-1",
                "resourceId", "resource-1",
                "actor", "forged-user"), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(resourceService).createResource(command.capture());
        assertEquals("alice", command.getValue().get("actor"));
        verify(workspacePort).materializeResource(resource);
        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq("project-1"),
                eq("project-resource"),
                eq("create"),
                eq("resource-1"),
                org.mockito.ArgumentMatchers.isNull(),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
    }

    @Test
    void generateMcpOverridesUntrustedActorAndMaterializesAggregate() {
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ProjectDefaultAgentPort defaultAgentPort = mock(ProjectDefaultAgentPort.class);
        ProjectAuditPort auditPort = mock(ProjectAuditPort.class);
        ProjectMcpGenerationApplicationService generationService =
                mock(ProjectMcpGenerationApplicationService.class);
        ManageProjectWorkspaceUseCase useCase =
                new ManageProjectWorkspaceUseCase(
                        workspacePort, defaultAgentPort, auditPort,
                        mock(ProjectMemberApplicationService.class),
                        mock(ProjectDefinitionApplicationService.class),
                        mock(ProjectResourceApplicationService.class), generationService,
                        mock(ProjectMcpManagementApplicationService.class),
                        mock(ProjectMcpCatalogApplicationService.class));
        ProjectMcpDefinition mcp = mcp("orders-mcp");
        when(generationService.generateDefinition(anyMap())).thenReturn(mcp);
        when(workspacePort.detail("project-1")).thenReturn(Map.of(
                "projectId", "project-1",
                "generatedMcps", List.of(Map.of("mcpId", "orders-mcp"))));

        useCase.generateMcp(Map.of(
                "projectId", "project-1",
                "resourceId", "orders-db",
                "actor", "forged-user"), "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(generationService).generateDefinition(command.capture());
        assertEquals("alice", command.getValue().get("actor"));
        verify(workspacePort).materializeMcp(mcp);
        verify(auditPort).record(
                eq("project-1"),
                eq("project-mcp"),
                eq("generate"),
                eq("orders-mcp"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateProjectToolOverridesUntrustedActorAndAuditsPrincipal() {
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ProjectDefaultAgentPort defaultAgentPort = mock(ProjectDefaultAgentPort.class);
        ProjectAuditPort auditPort = mock(ProjectAuditPort.class);
        ProjectMcpManagementApplicationService managementService =
                mock(ProjectMcpManagementApplicationService.class);
        ProjectMcpCatalogApplicationService catalogService =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectMcpDefinition currentDefinition = mock(ProjectMcpDefinition.class);
        ProjectMcpDefinition updatedDefinition = mcp("tool-1");
        ManageProjectWorkspaceUseCase useCase =
                new ManageProjectWorkspaceUseCase(
                        workspacePort, defaultAgentPort, auditPort,
                        mock(ProjectMemberApplicationService.class),
                        mock(ProjectDefinitionApplicationService.class),
                        mock(ProjectResourceApplicationService.class),
                        mock(ProjectMcpGenerationApplicationService.class),
                        managementService,
                        catalogService);
        when(catalogService.find("project-1", "tool-1"))
                .thenReturn(Optional.of(currentDefinition));
        when(catalogService.view(currentDefinition))
                .thenReturn(Map.of("toolId", "tool-1", "status", "ENABLED"));
        when(managementService.updateDefinition(eq("project-1"), eq("tool-1"), anyMap()))
                .thenReturn(updatedDefinition);
        when(catalogService.view(updatedDefinition))
                .thenReturn(Map.of("toolId", "tool-1", "status", "ENABLED"));

        useCase.updateProjectTool(
                "project-1",
                "tool-1",
                Map.of("actor", "forged-user", "requestTimeout", 30),
                "alice");

        ArgumentCaptor<Map<String, Object>> command = mapCaptor();
        verify(managementService).updateDefinition(
                eq("project-1"), eq("tool-1"), command.capture());
        assertEquals("alice", command.getValue().get("actor"));
        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq("project-1"),
                eq("project-tool"),
                eq("update"),
                eq("tool-1"),
                eq(Map.of("toolId", "tool-1", "status", "ENABLED")),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
    }

    @Test
    void updateProjectToolStatusRejectsMissingAuthenticatedActor() {
        ProjectWorkspacePort workspacePort = mock(ProjectWorkspacePort.class);
        ProjectDefaultAgentPort defaultAgentPort = mock(ProjectDefaultAgentPort.class);
        ProjectAuditPort auditPort = mock(ProjectAuditPort.class);
        ManageProjectWorkspaceUseCase useCase =
                new ManageProjectWorkspaceUseCase(
                        workspacePort, defaultAgentPort, auditPort,
                        mock(ProjectMemberApplicationService.class),
                        mock(ProjectDefinitionApplicationService.class),
                        mock(ProjectResourceApplicationService.class),
                        mock(ProjectMcpGenerationApplicationService.class),
                        mock(ProjectMcpManagementApplicationService.class),
                        mock(ProjectMcpCatalogApplicationService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.updateProjectToolStatus(
                        "project-1", "tool-1", "DISABLED", " "));

        assertEquals("PROJECT_ACTOR_REQUIRED", error.getMessage());
    }

    @Test
    void narrowPortsSeparateAggregateQueriesFromProjectionWrites() {
        ProjectWorkspaceQueryPort queries = mock(ProjectWorkspaceQueryPort.class);
        ProjectWorkspaceProjectionPort projection = mock(ProjectWorkspaceProjectionPort.class);
        ProjectDefaultAgentPort defaultAgentPort = mock(ProjectDefaultAgentPort.class);
        ProjectDefinitionApplicationService definitions =
                mock(ProjectDefinitionApplicationService.class);
        ProjectDefinition created = project("");
        ProjectDefinition updatedDefinition = project("agent-1");
        Map<String, Object> request = Map.of(
                "projectId", "project-1",
                "name", "Project One");
        Map<String, Object> updated = Map.of(
                "projectId", "project-1",
                "name", "Project One",
                "defaultAgentId", "agent-1");
        when(definitions.createDefinition(anyMap())).thenReturn(created);
        when(defaultAgentPort.ensure("project-1", "Project One"))
                .thenReturn(new ProjectDefaultAgentResult("agent-1"));
        when(definitions.assignDefaultAgent("project-1", "agent-1")).thenReturn(updatedDefinition);
        when(queries.detail("project-1")).thenReturn(updated);
        ManageProjectWorkspaceUseCase useCase = new ManageProjectWorkspaceUseCase(
                queries,
                projection,
                defaultAgentPort,
                mock(ProjectAuditPort.class),
                mock(ProjectMemberApplicationService.class),
                definitions,
                mock(ProjectResourceApplicationService.class),
                mock(ProjectMcpGenerationApplicationService.class),
                mock(ProjectMcpManagementApplicationService.class),
                mock(ProjectMcpCatalogApplicationService.class));

        Map<String, Object> result = useCase.createProject(request, "alice");

        assertEquals(updated, result);
        verify(projection).materializeDefinition(created);
        verify(projection).materializeDefinition(updatedDefinition);
        verify(queries).detail("project-1");
    }

    private ProjectDefinition project(String defaultAgentId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        return new ProjectDefinition(
                "project-1", "Project One", "", "alice", List.of("prod"), "",
                defaultAgentId, List.of(), List.of(), true, now, now);
    }

    private ProjectResourceDefinition resource(String resourceId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        return new ProjectResourceDefinition(
                resourceId, "project-1", ProjectResourceType.from("mysql"), "MySQL",
                "Orders DB", "prod", "jdbc:mysql://orders", Map.of(), "PREVIEW",
                Map.of(), Map.of(), now, now);
    }

    private ProjectMcpDefinition mcp(String mcpId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        return new ProjectMcpDefinition(
                mcpId, "Orders MCP", "project-1", "orders-db", "mysql", "stdio", "",
                Map.of(), List.of("SELECT"), ProjectMcpRiskLevel.LOW, true, Map.of(), 15,
                ProjectMcpStatus.ENABLED, now, now);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
