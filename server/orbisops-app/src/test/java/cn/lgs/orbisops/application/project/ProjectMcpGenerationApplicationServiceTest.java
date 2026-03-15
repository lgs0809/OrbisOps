package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMcpGenerationApplicationServiceTest {

    @Test
    void generatesTypedMcpFromPersistedProjectResource() {
        Map<String, ProjectDefinition> projects = new LinkedHashMap<>();
        ProjectDefinitionApplicationService definitionService =
                new ProjectDefinitionApplicationService(definitionRepository(projects), ignored -> { });
        definitionService.create(Map.of("projectId", "project-1", "name", "Project One"));

        Map<String, ProjectResourceDefinition> resources = new LinkedHashMap<>();
        IProjectResourceRepository resourceRepository = resourceRepository(resources);
        ProjectResourcePreparationPort resourcePreparationPort =
                new ProjectResourcePreparationPort() {
                    @Override
                    public ProjectResourcePreparation prepare(
                            ProjectResourcePreparationRequest request) {
                        return new ProjectResourcePreparation(
                                "MySQL",
                                Map.of("username", "reader", "passwordRef", "${env:DB_PASSWORD}"),
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
        ProjectResourceApplicationService resourceService = new ProjectResourceApplicationService(
                resourceRepository,
                definitionService,
                resourcePreparationPort);
        resourceService.create(Map.of(
                "projectId", "project-1",
                "resourceId", "orders-db",
                "type", "mysql",
                "environment", "PROD",
                "name", "Orders DB"));
        resourceService.create(Map.of(
                "projectId", "project-1",
                "resourceId", "billing-db",
                "type", "mysql",
                "environment", "PROD",
                "name", "Billing DB"));

        Map<String, ProjectMcpDefinition> definitions = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService catalogService =
                new ProjectMcpCatalogApplicationService(mcpRepository(definitions));
        ProjectMcpGenerationPreparationPort preparationPort = request ->
                new ProjectMcpGenerationPreparation(
                        "Orders DB readonly MCP",
                        "mysql",
                        "mysql-readonly-template",
                        "stdio",
                        Map.of(
                                "generated", true,
                                "projectId", request.projectId(),
                                "resourceId", request.resourceId(),
                                "remoteToolMetadata", Map.of("query_slow_log", Map.of("readOnly", true))),
                        List.of("SELECT", "EXPLAIN"),
                        "LOW",
                        true,
                        Map.of("actions", List.of("SELECT", "EXPLAIN")),
                        15,
                        "ENABLED");
        ProjectMcpGenerationApplicationService service =
                new ProjectMcpGenerationApplicationService(
                        resourceService, catalogService, preparationPort);

        Map<String, Object> generated = service.generate(Map.of(
                "projectId", "project-1",
                "resourceId", "orders-db",
                "profile", "READ ONLY"));
        Map<String, Object> second = service.generate(Map.of(
                "projectId", "project-1",
                "resourceId", "billing-db",
                "profile", "READ ONLY"));

        assertEquals("project-1-orders-db-read-only-mcp", generated.get("mcpId"));
        assertEquals("project-1-billing-db-read-only-mcp", second.get("mcpId"));
        assertEquals("orders-db", generated.get("resourceId"));
        assertEquals("mysql-readonly-template", generated.get("templateId"));
        assertEquals(true, generated.get("readOnly"));
        assertEquals(15, generated.get("requestTimeout"));
        assertTrue(catalogService.find(
                "project-1", "project-1-orders-db-read-only-mcp").isPresent());
        assertTrue(catalogService.find(
                "project-1", "project-1-billing-db-read-only-mcp").isPresent());
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
            public Optional<ProjectResourceDefinition> find(String projectId, String resourceId) {
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
}
