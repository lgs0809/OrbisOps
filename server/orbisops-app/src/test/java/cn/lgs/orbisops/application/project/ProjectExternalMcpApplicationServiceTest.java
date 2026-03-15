package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectExternalMcpApplicationServiceTest {

    @Test
    void registersAndRecordsDiscoveryWithDurableGovernanceMetadata() {
        Fixture fixture = fixture(value -> value != null && value.startsWith("${env:"));

        Map<String, Object> registered = fixture.service().register(
                "project-1",
                "Logs MCP",
                "Logs MCP",
                "https://mcp.example.com/mcp",
                "streamable-http",
                "${env:MCP_TOKEN}",
                "BEARER");

        assertEquals("logs-mcp", registered.get("mcpId"));
        assertEquals("REGISTERED", registered.get("connectionStatus"));
        assertEquals("PENDING_REVIEW", registered.get("policyStatus"));
        assertEquals("PENDING_REVIEW", registered.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> registeredConfig =
                (Map<String, Object>) registered.get("transportConfig");
        assertEquals("REGISTERED", registeredConfig.get("connectionStatus"));
        assertEquals("${env:MCP_TOKEN}", registeredConfig.get("credentialRef"));

        Map<String, Object> discovered = fixture.service().recordDiscovery(
                "project-1",
                "logs-mcp",
                List.of(Map.of(
                        "toolName", "search_logs",
                        "schemaHash", "schema-1")),
                "DISCOVERED",
                "");

        assertEquals("DISCOVERED", discovered.get("connectionStatus"));
        assertEquals("PENDING_REVIEW", discovered.get("policyStatus"));
        assertTrue(discovered.containsKey("remoteTools"));
        ProjectMcpDefinition stored = fixture.catalogService()
                .find("project-1", "logs-mcp")
                .orElseThrow();
        assertEquals("DISCOVERED",
                stored.transportConfig().get("connectionStatus"));
        assertEquals(List.of(Map.of(
                        "toolName", "search_logs",
                        "schemaHash", "schema-1")),
                stored.transportConfig().get("remoteTools"));
        assertEquals(2, fixture.workspace().materialized().size());
    }

    @Test
    void rejectsPlaintextBearerCredentialBeforeSaving() {
        Fixture fixture = fixture(value -> false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().register(
                        "project-1",
                        "logs-mcp",
                        "Logs MCP",
                        "https://mcp.example.com/mcp",
                        "streamable-http",
                        "plaintext-token",
                        "BEARER"));

        assertEquals(
                "MCP 凭据必须使用 ${env:NAME} 引用，不能保存明文 token",
                error.getMessage());
        assertEquals(List.of(), fixture.catalogService().list("project-1"));
        assertEquals(List.of(), fixture.workspace().materialized());
    }

    private Fixture fixture(
            ProjectExternalMcpCredentialReferencePort credentialReferencePort) {
        Map<String, ProjectDefinition> projects = new LinkedHashMap<>();
        ProjectDefinitionApplicationService definitionService =
                new ProjectDefinitionApplicationService(
                        definitionRepository(projects), ignored -> { });
        definitionService.create(Map.of(
                "projectId", "project-1",
                "name", "Project One"));

        Map<String, ProjectMcpDefinition> mcps = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService catalogService =
                new ProjectMcpCatalogApplicationService(mcpRepository(mcps));
        RecordingWorkspacePort workspace = new RecordingWorkspacePort();
        ProjectExternalMcpApplicationService service =
                new ProjectExternalMcpApplicationService(
                        definitionService,
                        catalogService,
                        credentialReferencePort,
                        workspace);
        return new Fixture(service, catalogService, workspace);
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
            ProjectExternalMcpApplicationService service,
            ProjectMcpCatalogApplicationService catalogService,
            RecordingWorkspacePort workspace) {
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
