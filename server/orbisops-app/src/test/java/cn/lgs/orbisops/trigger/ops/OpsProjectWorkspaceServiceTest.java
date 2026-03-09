package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.project.ProjectDefaultAgentPublicationPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparationPort;
import cn.lgs.orbisops.application.project.ProjectMemberApplicationService;
import cn.lgs.orbisops.application.project.ProjectResourceApplicationService;
import cn.lgs.orbisops.application.project.ProjectResourcePreparation;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationPort;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationRequest;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectWorkspaceReadinessRepository;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.trigger.application.project.OpsProjectCapabilityMetadataPolicy;
import cn.lgs.orbisops.trigger.application.project.OpsProjectMcpGenerationPreparationFactory;
import cn.lgs.orbisops.trigger.application.project.OpsProjectResourceCredentialPolicy;
import cn.lgs.orbisops.trigger.application.project.OpsProjectResourcePreparationService;
import cn.lgs.orbisops.trigger.application.project.OpsProjectResourceSchemaScanner;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceCompatibilityPayloadStore;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceMaterializationMapper;
import cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceProjectionMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectWorkspaceServiceTest {

    private final Map<OpsProjectWorkspaceService, ProjectResourceApplicationService>
            resourceServices = new IdentityHashMap<>();
    private final Map<OpsProjectWorkspaceService, ProjectMcpGenerationApplicationService>
            mcpGenerationServices = new IdentityHashMap<>();
    private final Map<OpsProjectWorkspaceService, IProjectWorkspaceReadinessRepository>
            readinessRepositories = new IdentityHashMap<>();
    private final Map<OpsProjectWorkspaceService, ProjectDefaultAgentPublicationPort>
            agentPublicationPorts = new IdentityHashMap<>();

    @Test
    void readinessRequiresPublishedDefaultAgentInsteadOfOnlyAnAgentId() {
        OpsProjectWorkspaceService service = workspaceService();

        Map<String, Object> project = service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "defaultAgentId", "demo-ops-agent"));

        assertEquals(false, project.get("defaultAgentPublished"));
        assertEquals(false, project.get("readyForInvestigation"));
        assertEquals("DEFAULT_AGENT_NOT_PUBLISHED", project.get("readinessReason"));
    }

    @Test
    void publishedDefaultAgentCompletesAgentOnboarding() {
        OpsProjectWorkspaceService service = workspaceService();
        publishAgent(service, "demo-project", "demo-ops-agent");

        Map<String, Object> project = service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "defaultAgentId", "demo-ops-agent"));

        assertEquals(true, project.get("defaultAgentPublished"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> onboarding = (List<Map<String, Object>>) project.get("onboarding");
        assertTrue(onboarding.stream().anyMatch(step -> "agent".equals(step.get("key"))
                && Boolean.TRUE.equals(step.get("completed"))));
    }

    @Test
    void registeredRepositoryOrExecutionResourceCompletesResourceOnboarding() {
        OpsProjectWorkspaceService service = workspaceService();
        IProjectWorkspaceReadinessRepository readinessRepository = readinessRepository(service);
        when(readinessRepository.available()).thenReturn(true);
        when(readinessRepository.countReadySourceRepositories("demo-project")).thenReturn(1);
        when(readinessRepository.countEnabledExecutionResources("demo-project")).thenReturn(2);
        publishAgent(service, "demo-project", "demo-ops-agent");

        Map<String, Object> project = service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "defaultAgentId", "demo-ops-agent"));

        assertEquals(1, project.get("sourceRepositoryCount"));
        assertEquals(2, project.get("executionResourceCount"));
        assertEquals(3, project.get("resourceCount"));
        assertEquals(true, project.get("readyForInvestigation"));
        assertEquals("READY", project.get("readinessReason"));
    }

    @Test
    void unavailableReadinessRepositoryFallsBackToZeroCounts() {
        OpsProjectWorkspaceService service = workspaceService();
        publishAgent(service, "demo-project", "demo-ops-agent");

        Map<String, Object> project = service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "defaultAgentId", "demo-ops-agent"));

        assertEquals(0, project.get("sourceRepositoryCount"));
        assertEquals(0, project.get("executionResourceCount"));
        assertEquals(0, project.get("resourceCount"));
        assertEquals(false, project.get("readyForInvestigation"));
        assertEquals("NO_RESOURCE_CONNECTED", project.get("readinessReason"));
    }

    @Test
    void readinessRepositoryFailureDoesNotHideProjectDataResources() {
        OpsProjectWorkspaceService service = workspaceService();
        IProjectWorkspaceReadinessRepository readinessRepository = readinessRepository(service);
        when(readinessRepository.available()).thenReturn(true);
        when(readinessRepository.countReadySourceRepositories("demo-project"))
                .thenThrow(new IllegalStateException("source store down"));
        when(readinessRepository.countEnabledExecutionResources("demo-project"))
                .thenThrow(new IllegalStateException("execution store down"));
        publishAgent(service, "demo-project", "demo-ops-agent");

        service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "defaultAgentId", "demo-ops-agent"));
        Map<String, Object> project = addResource(service, Map.of(
                "projectId", "demo-project",
                "resourceId", "demo-project-mysql",
                "type", "mysql",
                "name", "示例只读数据库",
                "status", "ENABLED",
                "endpoint", "mysql://127.0.0.1:3306/demo_db"));

        assertEquals(1, project.get("dataResourceCount"));
        assertEquals(0, project.get("sourceRepositoryCount"));
        assertEquals(0, project.get("executionResourceCount"));
        assertEquals(1, project.get("resourceCount"));
        assertEquals(true, project.get("readyForInvestigation"));
        assertEquals("READY", project.get("readinessReason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosticScenariosReflectRealProjectCapabilities() {
        OpsProjectWorkspaceService service = workspaceService();
        IProjectWorkspaceReadinessRepository readinessRepository = readinessRepository(service);
        when(readinessRepository.available()).thenReturn(true);
        when(readinessRepository.countReadySourceRepositories("demo-project")).thenReturn(0);
        when(readinessRepository.countEnabledExecutionResources("demo-project")).thenReturn(0);
        publishAgent(service, "demo-project", "demo-ops-agent");

        service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "defaultAgentId", "demo-ops-agent"));
        Map<String, Object> project = addResource(service, Map.of(
                "projectId", "demo-project",
                "resourceId", "demo-project-mysql",
                "type", "mysql",
                "name", "示例只读数据库",
                "status", "ENABLED",
                "endpoint", "mysql://127.0.0.1:3306/demo_db"));

        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) project.get("diagnosticScenarios");
        Map<String, Object> general = scenarios.stream()
                .filter(item -> "GENERAL_DIAGNOSIS".equals(item.get("scenarioId")))
                .findFirst().orElseThrow();
        Map<String, Object> slowSql = scenarios.stream()
                .filter(item -> "SLOW_SQL_ANALYSIS".equals(item.get("scenarioId")))
                .findFirst().orElseThrow();
        Map<String, Object> logs = scenarios.stream()
                .filter(item -> "LOG_ANALYSIS".equals(item.get("scenarioId")))
                .findFirst().orElseThrow();

        assertEquals(true, project.get("readyForInvestigation"));
        assertEquals("GENERAL_DIAGNOSIS", project.get("recommendedScenarioId"));
        assertEquals(true, general.get("ready"));
        assertEquals(true, slowSql.get("ready"));
        assertEquals(false, logs.get("ready"));
        assertTrue(String.valueOf(logs.get("unavailableReason")).contains("Elasticsearch"));

        Map<String, Object> catalogProject = service.publicCatalog().get(0);
        assertEquals(true, catalogProject.get("readyForInvestigation"));
        assertEquals("GENERAL_DIAGNOSIS", catalogProject.get("recommendedScenarioId"));
        assertEquals(6, ((List<?>) catalogProject.get("diagnosticScenarios")).size());
        assertEquals(false, catalogProject.containsKey("resources"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedMysqlMcpContainsRemoteToolLevelSafetyMetadata() {
        OpsProjectWorkspaceService service = workspaceService();
        service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "owner", "ops_user"));
        addResource(service, Map.of(
                "projectId", "demo-project",
                "resourceId", "demo-project-mysql-prod",
                "type", "mysql",
                "name", "示例 MySQL",
                "endpoint", "mysql://127.0.0.1:3306/demo_db"));

        Map<String, Object> project = generateMcp(service, Map.of(
                "projectId", "demo-project",
                "resourceId", "demo-project-mysql-prod",
                "profile", "readonly"));

        List<Map<String, Object>> generatedMcps = (List<Map<String, Object>>) project.get("generatedMcps");
        Map<String, Object> mcp = generatedMcps.get(0);
        Map<String, Object> remoteMetadata = (Map<String, Object>) mcp.get("remoteToolMetadata");
        Map<String, Object> slowLog = (Map<String, Object>) remoteMetadata.get("query_slow_log");
        Map<String, Object> transportConfig = (Map<String, Object>) mcp.get("transportConfig");
        Map<String, Object> nestedMetadata = (Map<String, Object>) transportConfig.get("remoteToolMetadata");

        assertTrue(remoteMetadata.containsKey("mysql_health"));
        assertTrue(remoteMetadata.containsKey("explain_select"));
        assertEquals(true, slowLog.get("readOnly"));
        assertEquals("LOW", slowLog.get("riskLevel"));
        assertTrue(nestedMetadata.containsKey("query_slow_log"));
    }

    @Test
    void shouldGrantPresetMemberWithoutReplacingExistingProjectConfiguration() {
        OpsProjectWorkspaceService service = workspaceService();
        service.createProject(Map.of(
                "projectId", "demo-project",
                "name", "示例系统",
                "owner", "ops-admin"));
        ProjectMemberApplicationService memberService = mock(ProjectMemberApplicationService.class);
        ReflectionTestUtils.setField(service, "projectMemberService", memberService);
        when(memberService.grantIfAbsent(
                "demo-project", "10001", "admin", "OWNER", "test-bootstrap"))
                .thenReturn(Map.of(
                        "projectId", "demo-project",
                        "memberKey", "10001",
                        "userId", "10001",
                        "username", "admin",
                        "memberRole", "OWNER"));

        Map<String, Object> member = service.grantProjectMemberIfAbsent(
                "demo-project", "10001", "admin", "OWNER", "test-bootstrap");

        assertEquals("demo-project", member.get("projectId"));
        assertEquals("10001", member.get("memberKey"));
        assertEquals("OWNER", member.get("memberRole"));
        verify(memberService).grantIfAbsent(
                "demo-project", "10001", "admin", "OWNER", "test-bootstrap");
    }

    private OpsProjectWorkspaceService workspaceService() {
        Map<String, ProjectDefinition> definitions = new LinkedHashMap<>();
        IProjectDefinitionRepository repository = new IProjectDefinitionRepository() {
            @Override
            public List<ProjectDefinition> listEnabled() {
                return definitions.values().stream()
                        .filter(ProjectDefinition::enabled)
                        .toList();
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
        ProjectDefinitionApplicationService definitionService =
                new ProjectDefinitionApplicationService(repository, ignored -> { });
        OpsProjectWorkspaceService service = new OpsProjectWorkspaceService();
        IProjectWorkspaceReadinessRepository readinessRepository =
                mock(IProjectWorkspaceReadinessRepository.class);
        when(readinessRepository.available()).thenReturn(false);
        ProjectDefaultAgentPublicationPort agentPublicationPort =
                mock(ProjectDefaultAgentPublicationPort.class);
        ProjectWorkspaceProjectionApplicationService projectionService =
                new ProjectWorkspaceProjectionApplicationService(
                        readinessRepository,
                        agentPublicationPort,
                        (catalog, projectId, error) -> { });
        ReflectionTestUtils.setField(service, "workspaceProjectionService", projectionService);
        ReflectionTestUtils.setField(service, "workspaceProjectionMapper", new OpsProjectWorkspaceProjectionMapper());
        readinessRepositories.put(service, readinessRepository);
        agentPublicationPorts.put(service, agentPublicationPort);
        ReflectionTestUtils.setField(service, "projectDefinitionService", definitionService);
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        OpsProjectResourceCredentialPolicy credentialPolicy =
                new OpsProjectResourceCredentialPolicy(secretResolver);
        OpsProjectResourceSchemaScanner schemaScanner = mock(OpsProjectResourceSchemaScanner.class);
        when(schemaScanner.scan(anyString(), anyString(), any(Map.class))).thenReturn(Map.of(
                "objects", List.of(),
                "source", "unavailable",
                "message", "test schema preview"));
        OpsProjectResourcePreparationService resourcePreparationService =
                new OpsProjectResourcePreparationService(credentialPolicy, schemaScanner);

        Map<String, ProjectResourceDefinition> projectResources = new LinkedHashMap<>();
        IProjectResourceRepository resourceRepository = new IProjectResourceRepository() {
            @Override
            public List<ProjectResourceDefinition> list(String projectId) {
                return projectResources.values().stream()
                        .filter(resource -> projectId.equals(resource.projectId()))
                        .toList();
            }

            @Override
            public Optional<ProjectResourceDefinition> find(String projectId, String resourceId) {
                ProjectResourceDefinition resource = projectResources.get(resourceId);
                return resource != null && projectId.equals(resource.projectId())
                        ? Optional.of(resource)
                        : Optional.empty();
            }

            @Override
            public ProjectResourceDefinition save(ProjectResourceDefinition resource) {
                projectResources.put(resource.resourceId(), resource);
                return resource;
            }
        };
        ProjectResourcePreparationPort preparationPort = new ProjectResourcePreparationPort() {
            @Override
            public ProjectResourcePreparation prepare(
                    ProjectResourcePreparationRequest request) {
                return resourcePreparationService.prepare(request);
            }

            @Override
            public Map<String, Object> enrichPermission(
                    String type,
                    Map<String, Object> permission) {
                return OpsProjectCapabilityMetadataPolicy.enrichPermission(type, permission);
            }
        };
        resourceServices.put(service, new ProjectResourceApplicationService(
                resourceRepository, definitionService, preparationPort));

        Map<String, ProjectMcpDefinition> projectMcps = new LinkedHashMap<>();
        IProjectMcpRepository mcpRepository = new IProjectMcpRepository() {
            @Override
            public List<ProjectMcpDefinition> listAll() {
                return List.copyOf(projectMcps.values());
            }

            @Override
            public List<ProjectMcpDefinition> list(String projectId) {
                return projectMcps.values().stream()
                        .filter(definition -> projectId.equals(definition.projectId()))
                        .toList();
            }

            @Override
            public List<ProjectMcpDefinition> listByTemplate(String templateId) {
                return projectMcps.values().stream()
                        .filter(definition -> templateId.equals(definition.templateId()))
                        .toList();
            }

            @Override
            public Optional<ProjectMcpDefinition> find(String projectId, String mcpId) {
                return Optional.ofNullable(projectMcps.get(projectId + "::" + mcpId));
            }

            @Override
            public ProjectMcpDefinition save(ProjectMcpDefinition definition) {
                projectMcps.put(definition.projectId() + "::" + definition.mcpId(), definition);
                return definition;
            }
        };
        ProjectMcpCatalogApplicationService mcpCatalogService =
                new ProjectMcpCatalogApplicationService(mcpRepository);
        ProjectMcpGenerationPreparationPort mcpPreparationPort =
                new OpsProjectMcpGenerationPreparationFactory()::prepare;
        mcpGenerationServices.put(service, new ProjectMcpGenerationApplicationService(
                resourceServices.get(service), mcpCatalogService, mcpPreparationPort));

        ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory =
                new ProjectWorkspaceRuntimeDirectoryApplicationService(
                        repository::listEnabled,
                        () -> List.copyOf(projectResources.values()),
                        mcpRepository::listAll,
                        credentialPolicy,
                        error -> { });
        ReflectionTestUtils.setField(service, "runtimeDirectory", runtimeDirectory);
        ReflectionTestUtils.setField(
                service,
                "materializationMapper",
                new OpsProjectWorkspaceMaterializationMapper(credentialPolicy));
        ReflectionTestUtils.setField(
                service,
                "compatibilityPayloadStore",
                new OpsProjectWorkspaceCompatibilityPayloadStore());
        return service;
    }

    private IProjectWorkspaceReadinessRepository readinessRepository(
            OpsProjectWorkspaceService service) {
        return readinessRepositories.get(service);
    }

    private void publishAgent(OpsProjectWorkspaceService service,
                              String projectId,
                              String agentId) {
        ProjectDefaultAgentPublicationPort port = agentPublicationPorts.get(service);
        when(port.published(projectId, agentId)).thenReturn(true);
    }

    private Map<String, Object> addResource(
            OpsProjectWorkspaceService service,
            Map<String, Object> request) {
        ProjectResourceApplicationService resourceService = resourceServices.get(service);
        Map<String, Object> resource = resourceService.create(request);
        service.materializeProjectResource(resource);
        return service.projectDetail(String.valueOf(resource.get("projectId")));
    }

    private Map<String, Object> generateMcp(
            OpsProjectWorkspaceService service,
            Map<String, Object> request) {
        ProjectMcpGenerationApplicationService generationService =
                mcpGenerationServices.get(service);
        Map<String, Object> mcp = generationService.generate(request);
        service.materializeProjectMcp(mcp);
        return service.projectDetail(String.valueOf(mcp.get("projectId")));
    }
}
