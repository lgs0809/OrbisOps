package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectMcpAuthorizationApplicationServiceTest {

    @Test
    void mergesEnabledProjectMcpsAndSharedIdsWithoutDuplicates() {
        ProjectMcpCatalogApplicationService catalogService =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectDefinitionApplicationService definitionService =
                mock(ProjectDefinitionApplicationService.class);
        ProjectDefinition project = mock(ProjectDefinition.class);
        ProjectMcpDefinition enabled = definition(
                "orders-mcp", ProjectMcpStatus.ENABLED);
        ProjectMcpDefinition disabled = definition(
                "admin-mcp", ProjectMcpStatus.DISABLED);
        when(project.sharedMcpIds()).thenReturn(List.of("shared-mcp", "orders-mcp"));
        when(definitionService.findDefinition("project-1")).thenReturn(Optional.of(project));
        when(catalogService.list("project-1"))
                .thenReturn(List.of(enabled, disabled));
        ProjectMcpAuthorizationApplicationService service =
                new ProjectMcpAuthorizationApplicationService(
                        catalogService, definitionService);

        List<String> ids = service.enabledIds("project-1");

        assertEquals(List.of("orders-mcp", "shared-mcp"), ids);
        assertTrue(service.allows("project-1", "orders-mcp"));
        assertTrue(service.allows("project-1", "shared-mcp"));
        assertFalse(service.allows("project-1", "admin-mcp"));
    }

    @Test
    void rejectsMissingProjectAndBlankIdentifiers() {
        ProjectMcpCatalogApplicationService catalogService =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectDefinitionApplicationService definitionService =
                mock(ProjectDefinitionApplicationService.class);
        when(definitionService.findDefinition("missing")).thenReturn(Optional.empty());
        ProjectMcpAuthorizationApplicationService service =
                new ProjectMcpAuthorizationApplicationService(
                        catalogService, definitionService);

        assertEquals("PROJECT_ID_REQUIRED", assertThrows(
                IllegalArgumentException.class,
                () -> service.enabledIds(" ")).getMessage());
        assertEquals("项目不存在：missing", assertThrows(
                IllegalArgumentException.class,
                () -> service.enabledIds("missing")).getMessage());
        assertFalse(service.allows("", "mcp-1"));
        assertFalse(service.allows("project-1", ""));
    }

    private ProjectMcpDefinition definition(
            String mcpId,
            ProjectMcpStatus status) {
        ProjectMcpDefinition definition = mock(ProjectMcpDefinition.class);
        when(definition.mcpId()).thenReturn(mcpId);
        when(definition.status()).thenReturn(status);
        return definition;
    }
}
