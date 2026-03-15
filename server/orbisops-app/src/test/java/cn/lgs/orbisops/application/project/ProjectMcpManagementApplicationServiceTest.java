package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.mcp.McpReviewedToolPolicySnapshot;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectMcpManagementApplicationServiceTest {

    @Test
    void enablesReviewedExternalMcpAndPreservesDiscoveredTools() {
        Fixture fixture = fixture();
        fixture.catalogService().save(externalDefinition());
        McpReviewedToolPolicySnapshot reviewedPolicy = policy("schema-1", true);
        when(fixture.runtimeQueries().reviewedPolicies("project-1", 1000))
                .thenReturn(List.of(reviewedPolicy));

        Map<String, Object> enabled = fixture.service().updateStatus(
                "project-1", "logs-mcp", "ENABLED");

        assertEquals("ENABLED", enabled.get("status"));
        assertEquals(List.of("search_logs"), enabled.get("allowedActions"));
        assertEquals("ACTIVE", enabled.get("policyStatus"));
        assertEquals("ENABLED", enabled.get("connectionStatus"));
        assertTrue(enabled.containsKey("remoteTools"));
        ProjectMcpDefinition stored = fixture.catalogService()
                .find("project-1", "logs-mcp")
                .orElseThrow();
        assertEquals(remoteTools(), stored.transportConfig().get("remoteTools"));
        assertEquals("ACTIVE", stored.transportConfig().get("policyStatus"));
        assertEquals("ENABLED", stored.transportConfig().get("connectionStatus"));
        assertEquals(1, fixture.workspace().materialized().size());
        verify(fixture.preparationPort(), never()).enrichTransportMetadata(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void rejectsExternalEnableWithoutMatchingHumanReviewedPolicy() {
        Fixture fixture = fixture();
        fixture.catalogService().save(externalDefinition());
        McpReviewedToolPolicySnapshot stalePolicy = policy("stale-schema", true);
        when(fixture.runtimeQueries().reviewedPolicies("project-1", 1000))
                .thenReturn(List.of(stalePolicy));

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> fixture.service().updateStatus(
                        "project-1", "logs-mcp", "ENABLED"));

        assertEquals(
                "MCP_POLICY_REVIEW_REQUIRED：外部 MCP 至少需要一个 schemaHash 匹配的 ACTIVE/HUMAN_REVIEWED Tool Policy",
                error.getMessage());
        assertEquals("PENDING_REVIEW", fixture.catalogService()
                .find("project-1", "logs-mcp")
                .orElseThrow()
                .status()
                .name());
        assertEquals(List.of(), fixture.workspace().materialized());
    }

    @Test
    void enrichesInternalPermissionAndMetadataBeforeSaving() {
        Map<String, ProjectMcpDefinition> definitions = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService catalogService =
                new ProjectMcpCatalogApplicationService(repository(definitions));
        catalogService.save(Map.ofEntries(
                Map.entry("mcpId", "orders-mcp"),
                Map.entry("mcpName", "Orders MCP"),
                Map.entry("projectId", "project-1"),
                Map.entry("resourceId", "orders-db"),
                Map.entry("resourceType", "mysql"),
                Map.entry("transportType", "stdio"),
                Map.entry("transportConfig", Map.of("generated", true)),
                Map.entry("allowedActions", List.of("SELECT")),
                Map.entry("riskLevel", "LOW"),
                Map.entry("readOnly", true),
                Map.entry("permissionPolicy", Map.of("readOnly", true)),
                Map.entry("requestTimeout", 20),
                Map.entry("status", "ENABLED")));
        RecordingWorkspacePort workspace = new RecordingWorkspacePort();
        ProjectMcpUpdatePreparationPort preparationPort =
                new ProjectMcpUpdatePreparationPort() {
                    @Override
                    public Map<String, Object> enrichPermission(
                            String resourceType,
                            Map<String, Object> permission) {
                        Map<String, Object> result = new LinkedHashMap<>(permission);
                        result.put("enriched", true);
                        return result;
                    }

                    @Override
                    public Map<String, Object> enrichTransportMetadata(
                            String resourceType,
                            List<String> allowedActions,
                            Map<String, Object> transportConfig) {
                        Map<String, Object> result = new LinkedHashMap<>(transportConfig);
                        result.put("remoteToolMetadata", Map.of(
                                "query_slow_log", Map.of("readOnly", true)));
                        return result;
                    }
                };
        ProjectMcpManagementApplicationService service =
                new ProjectMcpManagementApplicationService(
                        catalogService,
                        workspace,
                        preparationPort,
                        mock(ProjectMcpReviewedPolicyPort.class));

        ProjectMcpDefinition updatedDefinition = service.updateDefinition(
                "project-1",
                "orders-mcp",
                Map.of(
                        "permissionPolicy", Map.of("maxRows", 50),
                        "allowedActions", List.of("SELECT", "EXPLAIN")));
        Map<String, Object> updated = catalogService.view(updatedDefinition);

        assertEquals(List.of("SELECT", "EXPLAIN"), updatedDefinition.allowedActions());
        @SuppressWarnings("unchecked")
        Map<String, Object> permission =
                (Map<String, Object>) updated.get("permissionPolicy");
        assertEquals(true, permission.get("enriched"));
        assertTrue(updated.containsKey("remoteToolMetadata"));
        assertEquals(1, workspace.materialized().size());
    }

    private McpReviewedToolPolicySnapshot policy(String schemaHash, boolean reviewed) {
        return new McpReviewedToolPolicySnapshot(
                "logs-mcp", "search_logs", schemaHash, reviewed);
    }

    private Fixture fixture() {
        Map<String, ProjectMcpDefinition> definitions = new LinkedHashMap<>();
        ProjectMcpCatalogApplicationService catalogService =
                new ProjectMcpCatalogApplicationService(repository(definitions));
        RecordingWorkspacePort workspace = new RecordingWorkspacePort();
        ProjectMcpUpdatePreparationPort preparationPort =
                mock(ProjectMcpUpdatePreparationPort.class);
        ProjectMcpReviewedPolicyPort runtimeQueries = mock(ProjectMcpReviewedPolicyPort.class);
        ProjectMcpManagementApplicationService service =
                new ProjectMcpManagementApplicationService(
                        catalogService,
                        workspace,
                        preparationPort,
                        runtimeQueries);
        return new Fixture(
                service,
                catalogService,
                workspace,
                preparationPort,
                runtimeQueries);
    }

    private Map<String, Object> externalDefinition() {
        Map<String, Object> transportConfig = new LinkedHashMap<>();
        transportConfig.put("endpoint", "https://mcp.example.com/mcp");
        transportConfig.put("authMode", "NONE");
        transportConfig.put("connectionStatus", "DISCOVERED");
        transportConfig.put("policyStatus", "PENDING_REVIEW");
        transportConfig.put("remoteTools", remoteTools());
        return Map.ofEntries(
                Map.entry("mcpId", "logs-mcp"),
                Map.entry("mcpName", "Logs MCP"),
                Map.entry("projectId", "project-1"),
                Map.entry("resourceId", ""),
                Map.entry("resourceType", "external_mcp"),
                Map.entry("transportType", "streamable-http"),
                Map.entry("transportConfig", transportConfig),
                Map.entry("allowedActions", List.of()),
                Map.entry("riskLevel", "HIGH"),
                Map.entry("readOnly", false),
                Map.entry("permissionPolicy", Map.of(
                        "source", "PLATFORM_TOOL_POLICY",
                        "reviewRequired", true)),
                Map.entry("requestTimeout", 30),
                Map.entry("status", "PENDING_REVIEW"));
    }

    private List<Map<String, Object>> remoteTools() {
        return List.of(Map.of(
                "toolName", "search_logs",
                "schemaHash", "schema-1"));
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

    private record Fixture(
            ProjectMcpManagementApplicationService service,
            ProjectMcpCatalogApplicationService catalogService,
            RecordingWorkspacePort workspace,
            ProjectMcpUpdatePreparationPort preparationPort,
            ProjectMcpReviewedPolicyPort runtimeQueries) {
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
