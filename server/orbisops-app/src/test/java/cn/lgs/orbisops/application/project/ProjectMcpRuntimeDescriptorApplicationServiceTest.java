package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectMcpRuntimeDescriptorApplicationServiceTest {

    @Test
    void resolvesEnabledInternalMcpWithTypedResource() {
        ProjectMcpCatalogApplicationService catalog =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectResourceApplicationService resources =
                mock(ProjectResourceApplicationService.class);
        ProjectMcpDefinition definition = definition(
                "project-1", "orders-mcp", "orders-db",
                "mysql", ProjectMcpStatus.ENABLED);
        ProjectResourceDefinition resource = resource("project-1", "orders-db");
        when(catalog.find("project-1", "orders-mcp"))
                .thenReturn(Optional.of(definition));
        when(resources.findOptionalResource("project-1", "orders-db"))
                .thenReturn(Optional.of(resource));
        ProjectMcpRuntimeDescriptorApplicationService service =
                new ProjectMcpRuntimeDescriptorApplicationService(
                        catalog, resources);

        ProjectMcpRuntimeDescriptor descriptor = service.resolveEnabled(
                "project-1", "orders-mcp").orElseThrow();

        assertEquals(definition, descriptor.mcp());
        assertEquals(resource, descriptor.resource());
        assertFalse(descriptor.external());
    }

    @Test
    void returnsEmptyWhenEnabledInternalMcpResourceIsMissing() {
        ProjectMcpCatalogApplicationService catalog =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectResourceApplicationService resources =
                mock(ProjectResourceApplicationService.class);
        ProjectMcpDefinition definition = definition(
                "project-1", "orders-mcp", "missing-db",
                "mysql", ProjectMcpStatus.ENABLED);
        when(catalog.find("project-1", "orders-mcp"))
                .thenReturn(Optional.of(definition));
        when(resources.findOptionalResource("project-1", "missing-db"))
                .thenReturn(Optional.empty());
        ProjectMcpRuntimeDescriptorApplicationService service =
                new ProjectMcpRuntimeDescriptorApplicationService(
                        catalog, resources);

        assertTrue(service.resolveEnabled(
                "project-1", "orders-mcp").isEmpty());
    }

    @Test
    void discoveryAcceptsPendingExternalButRuntimeDoesNot() {
        ProjectMcpCatalogApplicationService catalog =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectResourceApplicationService resources =
                mock(ProjectResourceApplicationService.class);
        ProjectMcpDefinition definition = definition(
                "project-1", "logs-mcp", "",
                "external_mcp", ProjectMcpStatus.PENDING_REVIEW);
        when(catalog.find("project-1", "logs-mcp"))
                .thenReturn(Optional.of(definition));
        ProjectMcpRuntimeDescriptorApplicationService service =
                new ProjectMcpRuntimeDescriptorApplicationService(
                        catalog, resources);

        assertTrue(service.resolveEnabled(
                "project-1", "logs-mcp").isEmpty());
        ProjectMcpRuntimeDescriptor descriptor = service.resolveForDiscovery(
                "project-1", "logs-mcp").orElseThrow();
        assertTrue(descriptor.external());
        assertNull(descriptor.resource());
    }

    @Test
    void resolvesEnabledMcpAcrossProjectsAndReportsExistence() {
        ProjectMcpCatalogApplicationService catalog =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectResourceApplicationService resources =
                mock(ProjectResourceApplicationService.class);
        ProjectMcpDefinition definition = definition(
                "project-2", "metrics-mcp", "metrics-source",
                "prometheus", ProjectMcpStatus.ENABLED);
        when(catalog.listAll()).thenReturn(List.of(definition));
        ProjectResourceDefinition resource = resource("project-2", "metrics-source");
        when(catalog.find("project-2", "metrics-mcp"))
                .thenReturn(Optional.of(definition));
        when(resources.findOptionalResource("project-2", "metrics-source"))
                .thenReturn(Optional.of(resource));
        ProjectMcpRuntimeDescriptorApplicationService service =
                new ProjectMcpRuntimeDescriptorApplicationService(
                        catalog, resources);

        assertTrue(service.existsEnabledAny("metrics-mcp"));
        assertTrue(service.resolveEnabledAny("metrics-mcp").isPresent());
        assertFalse(service.existsEnabledAny("missing"));
        verify(catalog).find("project-2", "metrics-mcp");
    }

    private ProjectResourceDefinition resource(String projectId, String resourceId) {
        ProjectResourceDefinition resource = mock(ProjectResourceDefinition.class);
        when(resource.projectId()).thenReturn(projectId);
        when(resource.resourceId()).thenReturn(resourceId);
        return resource;
    }

    private ProjectMcpDefinition definition(
            String projectId,
            String mcpId,
            String resourceId,
            String resourceType,
            ProjectMcpStatus status) {
        ProjectMcpDefinition definition = mock(ProjectMcpDefinition.class);
        when(definition.projectId()).thenReturn(projectId);
        when(definition.mcpId()).thenReturn(mcpId);
        when(definition.resourceId()).thenReturn(resourceId);
        when(definition.resourceType()).thenReturn(resourceType);
        when(definition.status()).thenReturn(status);
        return definition;
    }
}
