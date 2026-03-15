package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjection;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionRequest;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectWorkspaceViewAssemblerTest {

    @Test
    void detailAssemblesCompatibilityRuntimeAndAuthorizedProjection() {
        Fixture fixture = fixture(true, true, true);
        Map<String, Object> expected = Map.of("projectId", "project-1", "ready", true);
        when(fixture.projectionMapper.detail(
                fixture.projection,
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads))
                .thenReturn(expected);

        Map<String, Object> result = fixture.assembler.detail(fixture.project);

        assertEquals(expected, result);
        verify(fixture.materializationMapper).projectView(
                fixture.project,
                fixture.projectCompatibility);
        verify(fixture.materializationMapper).resourcePayload(
                fixture.resource,
                fixture.resourceCompatibility);
        verify(fixture.materializationMapper).mcpPayload(
                fixture.mcp,
                fixture.mcpCompatibility);
        verify(fixture.skillAuthorization).localIds("project-1");
        verify(fixture.skillAuthorization).globalIds("project-1");
        verify(fixture.knowledgeAuthorization).enabledIds("project-1");
        verify(fixture.knowledgeAuthorization).projectEntries("project-1");
        verify(fixture.knowledgeAuthorization).globalEntries("project-1");
        verify(fixture.projectionMapper).request(
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads,
                List.of("local-skill"),
                List.of("global-skill"),
                List.of("kb-project", "kb-global"),
                List.of(),
                List.of());
        verify(fixture.projectionService).project(fixture.request);
    }

    @Test
    void catalogUsesSameProjectionAssemblyAndCatalogMapper() {
        Fixture fixture = fixture(true, true, true);
        Map<String, Object> expected = Map.of("projectId", "project-1", "name", "Project One");
        when(fixture.projectionMapper.catalog(fixture.projection)).thenReturn(expected);

        Map<String, Object> result = fixture.assembler.catalog(fixture.project);

        assertEquals(expected, result);
        verify(fixture.projectionMapper).request(
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads,
                List.of("local-skill"),
                List.of("global-skill"),
                List.of("kb-project", "kb-global"),
                List.of(),
                List.of());
        verify(fixture.projectionMapper).catalog(fixture.projection);
    }

    @Test
    void missingAuthorizationServicesFallBackToRuntimeProjectSkillsAndKnowledge() {
        Fixture fixture = fixture(false, false, false);
        Map<String, Object> expected = Map.of("projectId", "project-1");
        when(fixture.projectionMapper.detail(
                fixture.projection,
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads))
                .thenReturn(expected);

        Map<String, Object> result = fixture.assembler.detail(fixture.project);

        assertEquals(expected, result);
        verify(fixture.projectionMapper).request(
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads,
                List.of("runtime-skill"),
                List.of(),
                List.of("runtime-kb"),
                List.of(),
                List.of());
    }

    @Test
    void legacyKnowledgeFallbackPrefersProjectDefinitionApplicationService() {
        Fixture fixture = fixture(false, false, true);
        when(fixture.projectDefinitionService.defaultKnowledgeBaseId("project-1"))
                .thenReturn("application-kb");
        when(fixture.projectionMapper.detail(
                fixture.projection,
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads))
                .thenReturn(Map.of("projectId", "project-1"));

        fixture.assembler.detail(fixture.project);

        verify(fixture.projectDefinitionService).defaultKnowledgeBaseId("project-1");
        verify(fixture.projectionMapper).request(
                fixture.projectPayload,
                fixture.resourcePayloads,
                fixture.mcpPayloads,
                List.of("runtime-skill"),
                List.of(),
                List.of("application-kb"),
                List.of(),
                List.of());
    }

    private Fixture fixture(
            boolean skillAuthorizationAvailable,
            boolean knowledgeAuthorizationAvailable,
            boolean projectDefinitionAvailable) {
        ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory =
                mock(ProjectWorkspaceRuntimeDirectoryApplicationService.class);
        OpsProjectWorkspaceMaterializationMapper materializationMapper =
                mock(OpsProjectWorkspaceMaterializationMapper.class);
        OpsProjectWorkspaceCompatibilityPayloadStore compatibilityPayloadStore =
                mock(OpsProjectWorkspaceCompatibilityPayloadStore.class);
        ProjectWorkspaceProjectionApplicationService projectionService =
                mock(ProjectWorkspaceProjectionApplicationService.class);
        OpsProjectWorkspaceProjectionMapper projectionMapper =
                mock(OpsProjectWorkspaceProjectionMapper.class);
        ProjectSkillAuthorizationApplicationService skillAuthorization =
                skillAuthorizationAvailable
                        ? mock(ProjectSkillAuthorizationApplicationService.class)
                        : null;
        ProjectKnowledgeAuthorizationApplicationService knowledgeAuthorization =
                knowledgeAuthorizationAvailable
                        ? mock(ProjectKnowledgeAuthorizationApplicationService.class)
                        : null;
        ProjectDefinitionApplicationService projectDefinitionService =
                projectDefinitionAvailable
                        ? mock(ProjectDefinitionApplicationService.class)
                        : null;

        LocalDateTime now = LocalDateTime.of(2026, 7, 27, 12, 0);
        ProjectDefinition project = new ProjectDefinition(
                "project-1",
                "Project One",
                "description",
                "owner",
                List.of("prod"),
                "runtime-kb",
                "agent-1",
                List.of("runtime-skill"),
                List.of("mcp-1"),
                true,
                now,
                now);
        ProjectResourceDefinition resource = new ProjectResourceDefinition(
                "resource-1",
                "project-1",
                ProjectResourceType.from("mysql"),
                "mysql",
                "Orders DB",
                "prod",
                "jdbc:mysql://db/orders",
                Map.of(),
                "READY",
                Map.of(),
                Map.of(),
                now,
                now);
        ProjectMcpDefinition mcp = new ProjectMcpDefinition(
                "mcp-1",
                "Orders MCP",
                "project-1",
                "resource-1",
                "mysql",
                "stdio",
                "template-1",
                Map.of(),
                List.of("query"),
                cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel.LOW,
                true,
                Map.of(),
                30,
                ProjectMcpStatus.ENABLED,
                now,
                now);
        Map<String, Object> projectCompatibility = Map.of("legacyProject", true);
        Map<String, Object> resourceCompatibility = Map.of("legacyResource", true);
        Map<String, Object> mcpCompatibility = Map.of("legacyMcp", true);
        Map<String, Object> projectPayload = Map.of(
                "projectId", "project-1",
                "name", "Project One");
        List<Map<String, Object>> resourcePayloads = List.of(Map.of(
                "resourceId", "resource-1"));
        List<Map<String, Object>> mcpPayloads = List.of(Map.of(
                "mcpId", "mcp-1"));
        ProjectWorkspaceProjectionRequest request = requestFixture();
        ProjectWorkspaceProjection projection = projectionFixture(request);

        when(runtimeDirectory.resources("project-1")).thenReturn(List.of(resource));
        when(runtimeDirectory.mcps("project-1")).thenReturn(List.of(mcp));
        when(runtimeDirectory.project("project-1")).thenReturn(Optional.of(project));
        when(compatibilityPayloadStore.project("project-1"))
                .thenReturn(Optional.of(projectCompatibility));
        when(compatibilityPayloadStore.resource("project-1", "resource-1"))
                .thenReturn(Optional.of(resourceCompatibility));
        when(compatibilityPayloadStore.mcp("project-1", "mcp-1"))
                .thenReturn(Optional.of(mcpCompatibility));
        when(materializationMapper.projectView(project, projectCompatibility))
                .thenReturn(projectPayload);
        when(materializationMapper.resourcePayload(resource, resourceCompatibility))
                .thenReturn(resourcePayloads.get(0));
        when(materializationMapper.mcpPayload(mcp, mcpCompatibility))
                .thenReturn(mcpPayloads.get(0));
        if (skillAuthorization != null) {
            when(skillAuthorization.localIds("project-1"))
                    .thenReturn(List.of("local-skill"));
            when(skillAuthorization.globalIds("project-1"))
                    .thenReturn(List.of("global-skill"));
        }
        if (knowledgeAuthorization != null) {
            when(knowledgeAuthorization.enabledIds("project-1"))
                    .thenReturn(List.of("kb-project", "kb-global"));
            when(knowledgeAuthorization.projectEntries("project-1"))
                    .thenReturn(List.of());
            when(knowledgeAuthorization.globalEntries("project-1"))
                    .thenReturn(List.of());
        }
        when(projectionMapper.request(
                eq(projectPayload),
                eq(resourcePayloads),
                eq(mcpPayloads),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                anyList()))
                .thenReturn(request);
        when(projectionService.project(request)).thenReturn(projection);

        OpsProjectWorkspaceViewAssembler assembler =
                new OpsProjectWorkspaceViewAssembler(
                        runtimeDirectory,
                        materializationMapper,
                        compatibilityPayloadStore,
                        projectionService,
                        projectionMapper,
                        skillAuthorization,
                        knowledgeAuthorization,
                        projectDefinitionService);
        return new Fixture(
                assembler,
                runtimeDirectory,
                materializationMapper,
                compatibilityPayloadStore,
                projectionService,
                projectionMapper,
                skillAuthorization,
                knowledgeAuthorization,
                projectDefinitionService,
                project,
                resource,
                mcp,
                projectCompatibility,
                resourceCompatibility,
                mcpCompatibility,
                projectPayload,
                resourcePayloads,
                mcpPayloads,
                request,
                projection);
    }

    private ProjectWorkspaceProjectionRequest requestFixture() {
        return new ProjectWorkspaceProjectionRequest(
                new ProjectWorkspaceProjectionRequest.Project(
                        "project-1",
                        "Project One",
                        "description",
                        "owner",
                        List.of("prod"),
                        "runtime-kb",
                        "agent-1",
                        List.of(),
                        List.of("mcp-1"),
                        true,
                        "2026-07-27T12:00:00",
                        "2026-07-27T12:00:00"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                1);
    }

    private ProjectWorkspaceProjection projectionFixture(
            ProjectWorkspaceProjectionRequest request) {
        return new ProjectWorkspaceProjection(
                request.project(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                1,
                0,
                2,
                1,
                true,
                true,
                "ready",
                List.of(),
                "",
                List.of());
    }

    private record Fixture(
            OpsProjectWorkspaceViewAssembler assembler,
            ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory,
            OpsProjectWorkspaceMaterializationMapper materializationMapper,
            OpsProjectWorkspaceCompatibilityPayloadStore compatibilityPayloadStore,
            ProjectWorkspaceProjectionApplicationService projectionService,
            OpsProjectWorkspaceProjectionMapper projectionMapper,
            ProjectSkillAuthorizationApplicationService skillAuthorization,
            ProjectKnowledgeAuthorizationApplicationService knowledgeAuthorization,
            ProjectDefinitionApplicationService projectDefinitionService,
            ProjectDefinition project,
            ProjectResourceDefinition resource,
            ProjectMcpDefinition mcp,
            Map<String, Object> projectCompatibility,
            Map<String, Object> resourceCompatibility,
            Map<String, Object> mcpCompatibility,
            Map<String, Object> projectPayload,
            List<Map<String, Object>> resourcePayloads,
            List<Map<String, Object>> mcpPayloads,
            ProjectWorkspaceProjectionRequest request,
            ProjectWorkspaceProjection projection) {
    }
}
