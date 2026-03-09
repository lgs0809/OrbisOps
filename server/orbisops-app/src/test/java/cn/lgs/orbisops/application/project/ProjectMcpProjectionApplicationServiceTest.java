package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
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

class ProjectMcpProjectionApplicationServiceTest {

    @Test
    void savesDerivedProjectionBeforeMaterializingWorkspaceView() {
        Map<String, ProjectMcpDefinition> definitions = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService catalogService =
                new ProjectMcpCatalogApplicationService(repository(definitions));
        RecordingWorkspacePort workspace = new RecordingWorkspacePort();
        ProjectMcpProjectionApplicationService service =
                new ProjectMcpProjectionApplicationService(
                        catalogService, workspace);

        Map<String, Object> published = service.publish(Map.ofEntries(
                Map.entry("mcpId", "source-1-readonly-git-mcp"),
                Map.entry("mcpName", "Source Readonly Git MCP"),
                Map.entry("projectId", "project-1"),
                Map.entry("resourceId", "source-1"),
                Map.entry("resourceType", "git"),
                Map.entry("transportType", "stdio"),
                Map.entry("templateId", "git-readonly-template"),
                Map.entry("transportConfig", Map.of("repositoryId", "source-1")),
                Map.entry("allowedActions", List.of("git_read_file")),
                Map.entry("riskLevel", "LOW"),
                Map.entry("readOnly", true),
                Map.entry("permissionPolicy", Map.of("readOnly", true)),
                Map.entry("requestTimeout", 10),
                Map.entry("status", "ENABLED")));

        assertEquals("source-1-readonly-git-mcp", published.get("toolId"));
        assertEquals(true, published.get("readOnly"));
        assertTrue(catalogService.find(
                "project-1", "source-1-readonly-git-mcp").isPresent());
        assertEquals(1, workspace.materialized().size());
        ProjectMcpDefinition materialized = workspace.materialized().get(0);
        assertEquals("project-1", materialized.projectId());
        assertEquals("source-1-readonly-git-mcp", materialized.mcpId());
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

    private static final class RecordingWorkspacePort implements ProjectWorkspacePort {

        private final List<ProjectMcpDefinition> materialized =
                new java.util.ArrayList<>();

        @Override
        public Map<String, Object> snapshot() {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> templates() {
            return List.of();
        }

        @Override
        public Map<String, Object> detail(String projectId) {
            return Map.of();
        }

        @Override
        public void materializeDefinition(ProjectDefinition definition) {
        }

        @Override
        public void materializeResource(ProjectResourceDefinition resource) {
        }

        @Override
        public void materializeMcp(ProjectMcpDefinition mcp) {
            materialized.add(mcp);
        }

        private List<ProjectMcpDefinition> materialized() {
            return List.copyOf(materialized);
        }
    }
}
