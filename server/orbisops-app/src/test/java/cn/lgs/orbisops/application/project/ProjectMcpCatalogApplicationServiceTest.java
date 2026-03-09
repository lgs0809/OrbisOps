package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMcpCatalogApplicationServiceTest {

    @Test
    void mapsSavesAndQueriesTypedProjectMcpDefinitions() {
        Map<String, ProjectMcpDefinition> definitions = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService service =
                new ProjectMcpCatalogApplicationService(repository(definitions));

        ProjectMcpDefinition saved = service.save(Map.ofEntries(
                Map.entry("mcpId", "orders-readonly-mcp"),
                Map.entry("mcpName", "Orders Readonly MCP"),
                Map.entry("projectId", "project-1"),
                Map.entry("resourceId", "orders-db"),
                Map.entry("resourceType", "mysql"),
                Map.entry("transportType", "STDIO"),
                Map.entry("templateId", "mysql-readonly-template"),
                Map.entry("transportConfig", Map.of("generated", true)),
                Map.entry("allowedActions", List.of("SELECT", "EXPLAIN", "SELECT")),
                Map.entry("riskLevel", "low"),
                Map.entry("readOnly", true),
                Map.entry("permissionPolicy", Map.of("maxRows", 100)),
                Map.entry("requestTimeout", 15),
                Map.entry("status", "ENABLED"),
                Map.entry("createdAt", "2026-07-19T12:00:00")));

        assertEquals("stdio", saved.transportType());
        assertEquals(List.of("SELECT", "EXPLAIN"), saved.allowedActions());
        assertEquals(ProjectMcpRiskLevel.LOW, saved.riskLevel());
        assertEquals(ProjectMcpStatus.ENABLED, saved.status());
        assertEquals(1, service.list("project-1").size());
        assertEquals(1, service.listByTemplate("mysql-readonly-template").size());
        assertTrue(service.find("project-1", "orders-readonly-mcp").isPresent());

        Map<String, Object> view = service.view(saved);
        assertEquals("orders-readonly-mcp", view.get("toolId"));
        assertEquals("Orders Readonly MCP", view.get("toolName"));
        assertEquals("ENABLED", view.get("status"));
    }

    private IProjectMcpRepository repository(
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
