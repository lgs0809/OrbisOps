package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMcpTemplateGenerationApplicationServiceTest {

    @Test
    void generatesTemplateBackedProjectMcpThroughCatalogBoundary() {
        Fixture fixture = fixture();
        ProjectMcpTemplateGenerationPreparationPort preparationPort = request -> {
            assertEquals("mysql", request.resource().type().value());
            assertEquals("prod", request.resource().environment());
            return new ProjectMcpTemplateGenerationPreparation(
                        String.valueOf(request.command().get("toolName")),
                        "mysql",
                        request.template().templateId(),
                        "stdio",
                        Map.of(
                                "generated", true,
                                "projectId", request.projectId(),
                                "resourceId", request.resourceId(),
                                "remoteToolMetadata", Map.of(
                                        "query_slow_log", Map.of("readOnly", true))),
                        List.of("SELECT", "EXPLAIN"),
                        "LOW",
                        true,
                        Map.of("actions", List.of("SELECT", "EXPLAIN")),
                        20,
                        "ENABLED");
        };
        ProjectMcpTemplateGenerationApplicationService service =
                new ProjectMcpTemplateGenerationApplicationService(
                        fixture.resourceService(), fixture.catalogService(), preparationPort);

        McpTemplateDefinition template = template("mysql-readonly-template", "mysql");
        ProjectMcpDefinition generatedDefinition = service.generateDefinition(
                "project-1",
                template,
                Map.of(
                        "resourceId", "orders-db",
                        "toolId", "Orders Diagnostic Tool",
                        "toolName", "Orders Diagnostic MCP"));
        Map<String, Object> generated = fixture.catalogService().view(generatedDefinition);

        assertEquals("orders-diagnostic-tool", generated.get("mcpId"));
        assertEquals("orders-diagnostic-tool", generated.get("toolId"));
        assertEquals("mysql-readonly-template", generated.get("templateId"));
        assertEquals("Orders Diagnostic MCP", generated.get("toolName"));
        assertEquals(true, generated.get("readOnly"));
        assertTrue(generated.containsKey("remoteToolMetadata"));
        assertTrue(fixture.catalogService().find(
                "project-1", "orders-diagnostic-tool").isPresent());
    }

    @Test
    void preparationRequestRejectsMismatchedTypedResourceBinding() {
        Fixture fixture = fixture();
        ProjectResourceDefinition resource = fixture.resourceService()
                .requireResource("project-1", "orders-db");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectMcpTemplateGenerationPreparationRequest(
                        "project-2",
                        "orders-db",
                        "orders-mcp",
                        "readonly",
                        resource,
                        template("mysql-readonly-template", "mysql"),
                        Map.of()));

        assertEquals("PROJECT_MCP_RESOURCE_BINDING_MISMATCH", error.getMessage());
    }

    @Test
    void rejectsTemplateResourceTypeMismatchBeforePreparation() {
        Fixture fixture = fixture();
        ProjectMcpTemplateGenerationApplicationService service =
                new ProjectMcpTemplateGenerationApplicationService(
                        fixture.resourceService(),
                        fixture.catalogService(),
                        request -> {
                            throw new AssertionError("preparation must not run");
                        });

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateDefinition(
                        "project-1",
                        template("redis-template", "redis"),
                        Map.of("resourceId", "orders-db")));

        assertEquals("模板资源类型与项目资源不匹配：redis != mysql",
                error.getMessage());
    }

    private McpTemplateDefinition template(String templateId, String resourceType) {
        return new McpTemplateDefinition(
                templateId,
                "Template " + templateId,
                resourceType,
                "stdio",
                Map.of(),
                List.of("SELECT", "EXPLAIN"),
                "LOW",
                true,
                "",
                McpTemplateStatus.ENABLED,
                "tester");
    }

    private Fixture fixture() {
        Map<String, ProjectDefinition> projects = new LinkedHashMap<>();
        ProjectDefinitionApplicationService definitionService =
                new ProjectDefinitionApplicationService(
                        definitionRepository(projects), ignored -> { });
        definitionService.create(Map.of(
                "projectId", "project-1",
                "name", "Project One"));

        Map<String, ProjectResourceDefinition> resources = new LinkedHashMap<>();
        ProjectResourcePreparationPort resourcePreparationPort =
                new ProjectResourcePreparationPort() {
                    @Override
                    public ProjectResourcePreparation prepare(
                            ProjectResourcePreparationRequest request) {
                        return new ProjectResourcePreparation(
                                "MySQL",
                                Map.of(
                                        "username", "reader",
                                        "passwordRef", "${env:DB_PASSWORD}"),
                                Map.of("source", "preview", "objects", List.of()),
                                Map.of("actions", List.of("SELECT", "EXPLAIN")),
                                "PREVIEW");
                    }

                    @Override
                    public Map<String, Object> enrichPermission(
                            String type,
                            Map<String, Object> permission) {
                        return permission;
                    }
                };
        ProjectResourceApplicationService resourceService =
                new ProjectResourceApplicationService(
                        resourceRepository(resources),
                        definitionService,
                        resourcePreparationPort);
        resourceService.create(Map.of(
                "projectId", "project-1",
                "resourceId", "orders-db",
                "type", "mysql",
                "environment", "prod",
                "name", "Orders DB"));

        Map<String, ProjectMcpDefinition> mcps = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService catalogService =
                new ProjectMcpCatalogApplicationService(mcpRepository(mcps));
        return new Fixture(resourceService, catalogService);
    }

    private IProjectDefinitionRepository definitionRepository(
            Map<String, ProjectDefinition> definitions) {
        return new IProjectDefinitionRepository() {
            @Override
            public List<ProjectDefinition> listEnabled() {
                return List.copyOf(definitions.values());
            }

            @Override
            public Optional<ProjectDefinition> find(String projectId) {
                return Optional.ofNullable(definitions.get(projectId));
            }

            @Override
            public boolean exists(String projectId) {
                return definitions.containsKey(projectId);
            }

            @Override
            public ProjectDefinition save(ProjectDefinition definition) {
                definitions.put(definition.projectId(), definition);
                return definition;
            }
        };
    }

    private IProjectResourceRepository resourceRepository(
            Map<String, ProjectResourceDefinition> resources) {
        return new IProjectResourceRepository() {
            @Override
            public List<ProjectResourceDefinition> list(String projectId) {
                return resources.values().stream()
                        .filter(resource -> projectId.equals(resource.projectId()))
                        .toList();
            }

            @Override
            public Optional<ProjectResourceDefinition> find(
                    String projectId,
                    String resourceId) {
                return Optional.ofNullable(resources.get(projectId + "::" + resourceId));
            }

            @Override
            public ProjectResourceDefinition save(ProjectResourceDefinition resource) {
                resources.put(resource.projectId() + "::" + resource.resourceId(), resource);
                return resource;
            }
        };
    }

    private IProjectMcpRepository mcpRepository(
            Map<String, ProjectMcpDefinition> definitions) {
        return new IProjectMcpRepository() {
            @Override
            public List<ProjectMcpDefinition> listAll() {
                return List.copyOf(definitions.values());
            }

            @Override
            public List<ProjectMcpDefinition> list(String projectId) {
                return definitions.values().stream()
                        .filter(definition -> projectId.equals(definition.projectId()))
                        .toList();
            }

            @Override
            public List<ProjectMcpDefinition> listByTemplate(String templateId) {
                return definitions.values().stream()
                        .filter(definition -> templateId.equals(definition.templateId()))
                        .toList();
            }

            @Override
            public Optional<ProjectMcpDefinition> find(String projectId, String mcpId) {
                return Optional.ofNullable(definitions.get(projectId + "::" + mcpId));
            }

            @Override
            public ProjectMcpDefinition save(ProjectMcpDefinition definition) {
                definitions.put(definition.projectId() + "::" + definition.mcpId(), definition);
                return definition;
            }
        };
    }

    private record Fixture(
            ProjectResourceApplicationService resourceService,
            ProjectMcpCatalogApplicationService catalogService) {
    }
}
