package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpProjectToolDescriptor;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsMcpProjectToolCatalogAdapterTest {

    @Test
    void onlyPlatformGeneratedResourceBackedMcpGetsTrustedRemoteMetadataProvenance() {
        IProjectMcpRepository repository = mock(IProjectMcpRepository.class);
        ProjectMcpCatalogApplicationService catalog = new ProjectMcpCatalogApplicationService(repository);
        OpsMcpProjectToolCatalogAdapter adapter = new OpsMcpProjectToolCatalogAdapter(catalog);

        ProjectMcpDefinition generated = definition(
                "generated-openapi",
                "resource-1",
                "openapi-readonly-template",
                Map.of(
                        "generated", true,
                        "resourceId", "resource-1",
                        "serverTemplate", "openapi-policy-mcp",
                        "remoteToolMetadata", Map.of(
                                "openapi_list_operations", Map.of(
                                        "toolName", "openapi_list_operations",
                                        "readOnly", true,
                                        "riskLevel", "LOW",
                                        "allowedActions", List.of("READ_OPENAPI")))));
        ProjectMcpDefinition external = definition(
                "external-mcp",
                "",
                "",
                Map.of(
                        "remoteToolMetadata", Map.of(
                                "openapi_list_operations", Map.of(
                                        "toolName", "openapi_list_operations",
                                        "readOnly", true,
                                        "riskLevel", "LOW",
                                        "allowedActions", List.of("READ_OPENAPI")))));
        when(repository.find("project-1", "generated-openapi")).thenReturn(Optional.of(generated));
        when(repository.find("project-1", "external-mcp")).thenReturn(Optional.of(external));

        McpProjectToolDescriptor generatedDescriptor = adapter.find("project-1", "generated-openapi")
                .orElseThrow();
        McpProjectToolDescriptor externalDescriptor = adapter.find("project-1", "external-mcp")
                .orElseThrow();

        assertTrue(Boolean.TRUE.equals(generatedDescriptor
                .remoteTool("openapi_list_operations")
                .rawMetadata()
                .get("platformGenerated")));
        assertFalse(Boolean.TRUE.equals(externalDescriptor
                .remoteTool("openapi_list_operations")
                .rawMetadata()
                .get("platformGenerated")));
        assertEquals(List.of("READ_OPENAPI"), generatedDescriptor
                .remoteTool("openapi_list_operations")
                .allowedActions());
    }

    private ProjectMcpDefinition definition(String mcpId,
                                            String resourceId,
                                            String templateId,
                                            Map<String, Object> transportConfig) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 18, 0);
        return new ProjectMcpDefinition(
                mcpId,
                mcpId,
                "project-1",
                resourceId,
                "openapi",
                "stdio",
                templateId,
                transportConfig,
                List.of("READ_OPENAPI"),
                ProjectMcpRiskLevel.LOW,
                true,
                Map.of(),
                10,
                ProjectMcpStatus.ENABLED,
                now,
                now);
    }
}
