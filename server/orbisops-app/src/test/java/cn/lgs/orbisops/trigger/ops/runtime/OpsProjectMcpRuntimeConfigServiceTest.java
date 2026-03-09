package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptor;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectMcpRuntimeConfigServiceTest {

    @Test
    void buildsInternalAndExternalRuntimeConfigsThroughDedicatedFactory() {
        ProjectMcpRuntimeDescriptorApplicationService descriptors =
                mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        OpsProjectMcpRuntimeConfigFactory runtimeConfigFactory =
                mock(OpsProjectMcpRuntimeConfigFactory.class);
        OpsProjectMcpRuntimeConfigService service =
                new OpsProjectMcpRuntimeConfigService(
                        descriptors, runtimeConfigFactory);
        ProjectMcpRuntimeDescriptor internal = new ProjectMcpRuntimeDescriptor(
                mcp("orders-mcp", "orders-db", "mysql"),
                resource("orders-db"));
        ProjectMcpRuntimeDescriptor external = new ProjectMcpRuntimeDescriptor(
                mcp("logs-mcp", "", "external_mcp"),
                null);
        OpsMcpServerConfig internalConfig =
                OpsMcpServerConfig.builder().mcpId("orders-mcp").build();
        OpsMcpServerConfig externalConfig =
                OpsMcpServerConfig.builder().mcpId("logs-mcp").build();
        when(descriptors.resolveEnabled("project-1", "orders-mcp"))
                .thenReturn(Optional.of(internal));
        when(descriptors.resolveForDiscovery("project-1", "logs-mcp"))
                .thenReturn(Optional.of(external));
        when(runtimeConfigFactory.build(internal)).thenReturn(internalConfig);
        when(runtimeConfigFactory.build(external)).thenReturn(externalConfig);

        assertSame(internalConfig, service.resolve(
                "project-1", "orders-mcp").orElseThrow());
        assertSame(externalConfig, service.resolveForDiscovery(
                "project-1", "logs-mcp").orElseThrow());
        verify(runtimeConfigFactory).build(internal);
        verify(runtimeConfigFactory).build(external);
    }

    @Test
    void returnsEmptyWhenDescriptorIsUnavailable() {
        ProjectMcpRuntimeDescriptorApplicationService descriptors =
                mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        OpsProjectMcpRuntimeConfigFactory runtimeConfigFactory =
                mock(OpsProjectMcpRuntimeConfigFactory.class);
        OpsProjectMcpRuntimeConfigService service =
                new OpsProjectMcpRuntimeConfigService(
                        descriptors, runtimeConfigFactory);
        when(descriptors.resolveEnabled("project-1", "missing"))
                .thenReturn(Optional.empty());

        assertTrue(service.resolve("project-1", "missing").isEmpty());
    }

    private ProjectMcpDefinition mcp(
            String mcpId,
            String resourceId,
            String resourceType) {
        ProjectMcpDefinition definition = mock(ProjectMcpDefinition.class);
        when(definition.projectId()).thenReturn("project-1");
        when(definition.mcpId()).thenReturn(mcpId);
        when(definition.resourceId()).thenReturn(resourceId);
        when(definition.resourceType()).thenReturn(resourceType);
        return definition;
    }

    private ProjectResourceDefinition resource(String resourceId) {
        ProjectResourceDefinition resource = mock(ProjectResourceDefinition.class);
        when(resource.projectId()).thenReturn("project-1");
        when(resource.resourceId()).thenReturn(resourceId);
        return resource;
    }
}
