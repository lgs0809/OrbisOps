package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleUseCase;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentCapabilityApplicationService;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentCapabilityBindingEditor;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentCapabilityBindingPolicy;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentCapabilityCatalogService;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionDefaults;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionGateway;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionManagementAssembly;
import cn.lgs.orbisops.trigger.application.agenteval.OpsAgentEvalAdapter;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionApplicationServiceTest {

    @Test
    void defaultAgentMustPassStaticValidationAndEvalBeforePublish() {
        Fixture fixture = fixture();
        allowProject(fixture);
        OpsAgentDefinition template = OpsAgentDefinition.builder()
                .agentId(OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID)
                .name("默认运维 Agent")
                .build();
        when(fixture.definitionRegistry.listVersions("demo-project-ops-agent")).thenReturn(List.of());
        when(fixture.definitionRegistry.resolve(OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID, null, true))
                .thenReturn(template);
        when(fixture.lifecycleUseCase.saveDraft(any(OpsAgentDefinition.class))).thenAnswer(invocation -> {
            OpsAgentDefinition value = invocation.getArgument(0);
            value.setVersion(1);
            value.setLifecycle("DRAFT");
            return value;
        });
        when(fixture.lifecycleUseCase.validate("demo-project-ops-agent", 1)).thenAnswer(invocation -> {
            template.setAgentId("demo-project-ops-agent");
            template.setProjectId("demo-project");
            template.setVersion(1);
            template.setLifecycle("VALIDATED");
            template.setDefinitionHash("hash-1");
            return template;
        });
        AgentEvalSuite suite = new AgentEvalSuite(
                "suite-1",
                "demo-project",
                "demo-project-ops-agent",
                "release gate",
                1,
                List.of(mock(AgentEvalCase.class)),
                "SYSTEM_DEFAULT_AGENT_BOOTSTRAP");
        AgentEvalRunResult evaluation = new AgentEvalRunResult(
                "run-1",
                "suite-1",
                "demo-project",
                "demo-project-ops-agent",
                1,
                "hash-1",
                0,
                "PASSED",
                "PASSED",
                3,
                3,
                0,
                List.of(),
                List.of());
        when(fixture.agentEvalService.createReleaseSuite(any())).thenReturn(suite);
        when(fixture.agentEvalService.runReleaseEvaluation(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "suite-1",
                "SYSTEM_DEFAULT_AGENT_BOOTSTRAP"))
                .thenReturn(evaluation);
        when(fixture.lifecycleUseCase.publish("demo-project-ops-agent", 1)).thenAnswer(invocation -> {
            template.setLifecycle("PUBLISHED");
            return template;
        });

        Map<String, Object> result = fixture.service.createProjectDefaultAgent("demo-project", "示例系统");

        assertEquals("PUBLISHED", result.get("lifecycle"));
        verify(fixture.lifecycleUseCase).saveDraft(any(OpsAgentDefinition.class));
        verify(fixture.lifecycleUseCase).validate("demo-project-ops-agent", 1);
        verify(fixture.agentEvalService).createReleaseSuite(any());
        verify(fixture.agentEvalService).runReleaseEvaluation(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "suite-1",
                "SYSTEM_DEFAULT_AGENT_BOOTSTRAP");
        verify(fixture.agentEvalService).assertReleaseAllowed(
                "demo-project",
                "demo-project-ops-agent",
                1,
                "hash-1");
        verify(fixture.lifecycleUseCase).publish("demo-project-ops-agent", 1);
    }

    @Test
    void specializedWorkflowPublishMustRunDeterministicEvalBeforeLifecyclePublish() {
        Fixture fixture = fixture();
        allowProject(fixture);
        OpsAgentDefinition validated = OpsAgentDefinition.builder()
                .agentId("workflow-1")
                .projectId("demo-project")
                .version(2)
                .definitionHash("hash-2")
                .lifecycle("VALIDATED")
                .nodes(List.of(
                        OpsWorkflowNode.builder().nodeId("start").type("START").agent("start").build(),
                        OpsWorkflowNode.builder().nodeId("worker").type("AGENT").agent("worker").build(),
                        OpsWorkflowNode.builder().nodeId("end").type("END").agent("end").build()))
                .build();
        when(fixture.definitionRegistry.listVersions("workflow-1")).thenReturn(List.of(validated));
        when(fixture.agentEvalService.createSuite(eq("demo-project"), eq("workflow-1"), any(), eq("alice")))
                .thenReturn(Map.of("suiteId", "suite-workflow-1"));
        when(fixture.agentEvalService.run("demo-project", "workflow-1", 2, "suite-workflow-1", "alice"))
                .thenReturn(Map.of("status", "PASSED", "definitionHash", "hash-2"));
        when(fixture.lifecycleUseCase.publish("workflow-1", 2)).thenAnswer(invocation -> {
            validated.setLifecycle("PUBLISHED");
            return validated;
        });

        Map<String, Object> result = fixture.service.publishVersion("workflow-1", 2, "alice");

        assertEquals("PUBLISHED", result.get("lifecycle"));
        ArgumentCaptor<Map<String, Object>> suiteRequest = ArgumentCaptor.forClass(Map.class);
        verify(fixture.agentEvalService).createSuite(eq("demo-project"), eq("workflow-1"), suiteRequest.capture(), eq("alice"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) suiteRequest.getValue().get("cases");
        assertEquals(List.of("start", "worker", "end"), cases.get(0).get("requiredNodes"));
        verify(fixture.agentEvalService).run("demo-project", "workflow-1", 2, "suite-workflow-1", "alice");
        verify(fixture.lifecycleUseCase).publish("workflow-1", 2);
    }

    @Test
    void standaloneReactPublishUsesRoleAssertionWhenWorkflowNodesAreAbsent() {
        Fixture fixture = fixture();
        allowProject(fixture);
        OpsAgentDefinition validated = OpsAgentDefinition.builder()
                .agentId("react-1")
                .projectId("demo-project")
                .version(2)
                .definitionHash("hash-react-2")
                .lifecycle("VALIDATED")
                .engine("AGENTSCOPE")
                .agentscopeAgents(List.of(
                        OpsAgentScopeConfig.builder()
                                .agentId("main-assistant")
                                .role("MAIN_ASSISTANT")
                                .build()))
                .build();
        when(fixture.definitionRegistry.listVersions("react-1")).thenReturn(List.of(validated));
        when(fixture.agentEvalService.createSuite(eq("demo-project"), eq("react-1"), any(), eq("alice")))
                .thenReturn(Map.of("suiteId", "suite-react-1"));
        when(fixture.agentEvalService.run("demo-project", "react-1", 2, "suite-react-1", "alice"))
                .thenReturn(Map.of("status", "PASSED", "definitionHash", "hash-react-2"));
        when(fixture.lifecycleUseCase.publish("react-1", 2)).thenAnswer(invocation -> {
            validated.setLifecycle("PUBLISHED");
            return validated;
        });

        Map<String, Object> result = fixture.service.publishVersion("react-1", 2, "alice");

        assertEquals("PUBLISHED", result.get("lifecycle"));
        ArgumentCaptor<Map<String, Object>> suiteRequest = ArgumentCaptor.forClass(Map.class);
        verify(fixture.agentEvalService).createSuite(eq("demo-project"), eq("react-1"), suiteRequest.capture(), eq("alice"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) suiteRequest.getValue().get("cases");
        assertEquals(List.of("MAIN_ASSISTANT"), cases.get(0).get("requiredRoles"));
        assertFalse(cases.get(0).containsKey("requiredNodes"));
        verify(fixture.agentEvalService).run("demo-project", "react-1", 2, "suite-react-1", "alice");
        verify(fixture.lifecycleUseCase).publish("react-1", 2);
    }

    @Test
    void defaultAgentEnsureIsIdempotentWhenPublishedVersionExists() {
        Fixture fixture = fixture();
        allowProject(fixture);
        OpsAgentDefinition published = OpsAgentDefinition.builder()
                .agentId("demo-project-ops-agent")
                .projectId("demo-project")
                .version(4)
                .lifecycle("PUBLISHED")
                .agentscopeAgents(List.of(
                        OpsAgentScopeConfig.builder().role("MAIN_ASSISTANT").build()))
                .build();
        when(fixture.definitionRegistry.listVersions("demo-project-ops-agent")).thenReturn(List.of(published));

        Map<String, Object> result = fixture.service.createProjectDefaultAgent("demo-project", "示例系统");

        assertEquals(4, result.get("version"));
        verify(fixture.lifecycleUseCase, never()).saveDraft(any());
        verify(fixture.agentEvalService, never()).createReleaseSuite(any());
    }

    @Test
    void shouldExposeOnlyProjectAuthorizedCapabilities() {
        Fixture fixture = fixture();
        when(fixture.projectDefinitionService.exists("demo-project")).thenReturn(true);
        when(fixture.projectDefinitionService.find("demo-project")).thenReturn(Map.of(
                "projectId", "demo-project",
                "name", "示例系统"));
        ProjectMcpDefinition projectMcp = mock(ProjectMcpDefinition.class);
        when(fixture.projectMcpCatalogService.list("demo-project"))
                .thenReturn(List.of(projectMcp));
        when(fixture.projectMcpCatalogService.view(projectMcp)).thenReturn(Map.of(
                "mcpId", "demo-project-mysql-prod-readonly-mcp",
                "mcpName", "示例 MySQL 只读 MCP",
                "projectId", "demo-project",
                "resourceType", "mysql"));
        when(fixture.projectSkillAuthorizationService.enabledIds("demo-project")).thenReturn(List.of("demo-project-sop"));
        when(fixture.projectSkillAuthorizationService.localIds("demo-project")).thenReturn(List.of("demo-project-sop"));
        when(fixture.projectSkillAuthorizationService.globalIds("demo-project")).thenReturn(List.of("common-ops-report"));
        when(fixture.projectMcpAuthorizationService.enabledIds("demo-project")).thenReturn(List.of(
                "demo-project-mysql-prod-readonly-mcp",
                "shared-channel"));
        when(fixture.projectKnowledgeAuthorizationService.enabledIds("demo-project"))
                .thenReturn(List.of("demo-ops"));
        when(fixture.projectKnowledgeAuthorizationService.defaultId("demo-project"))
                .thenReturn("demo-ops");

        Map<String, Object> capabilities = fixture.service.projectAgentCapabilities("demo-project");

        assertEquals("demo-project", capabilities.get("projectId"));
        assertEquals(List.of("demo-project-sop", "common-ops-report"), capabilities.get("skillIds"));
        assertEquals(List.of("demo-ops"), capabilities.get("knowledgeBaseIds"));
        assertTrue(((List<?>) capabilities.get("projectSkills")).stream()
                .anyMatch(item -> "demo-project-sop".equals(((Map<?, ?>) item).get("skillId"))));
        assertTrue(((List<?>) capabilities.get("enabledGlobalSkills")).stream()
                .anyMatch(item -> "common-ops-report".equals(((Map<?, ?>) item).get("skillId"))));
        assertEquals(1, ((List<?>) capabilities.get("projectTools")).size());
        assertEquals(1, ((List<?>) capabilities.get("enabledSharedTools")).size());
    }

    @Test
    void shouldReportUnauthorizedBindings() {
        Fixture fixture = fixture();
        allowProject(fixture);
        when(fixture.projectSkillAuthorizationService.allows("demo-project", "demo-project-sop")).thenReturn(true);
        when(fixture.projectMcpAuthorizationService.allows("demo-project", "demo-project-mysql-prod-readonly-mcp")).thenReturn(true);
        when(fixture.projectKnowledgeAuthorizationService.allows("demo-project", "demo-ops")).thenReturn(true);
        Map<String, Object> result = fixture.service.validateBindings(OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .skills(List.of("demo-project-sop", "foreign-skill"))
                .mcpIds(List.of("demo-project-mysql-prod-readonly-mcp", "global-template-mcp"))
                .knowledgeBaseId("global-kb")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("mysql-agent")
                        .mcpIds(List.of("demo-project-mysql-prod-readonly-mcp"))
                        .knowledgeBaseId("demo-ops")
                        .build()))
                .build());

        assertFalse((Boolean) result.get("valid"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.stream().anyMatch(error -> error.contains("foreign-skill")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("global-template-mcp")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("global-kb")));
    }

    @Test
    void shouldRejectDraftSaveWhenBindingIsNotAuthorized() {
        Fixture fixture = fixture();
        allowProject(fixture);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .mcpIds(List.of("unbound-mcp"))
                .build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.saveDraft(definition));

        assertTrue(error.getMessage().contains("unbound-mcp"));
        verify(fixture.definitionRegistry, never()).saveDraft(definition);
    }

    @Test
    void shouldRejectInlineMcpServersBecauseTheyBypassProjectToolRouter() {
        Fixture fixture = fixture();
        allowProject(fixture);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .mcpServers(List.of(OpsMcpServerConfig.builder().name("raw-global-mcp").transport("sse").build()))
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("mysql-agent")
                        .mcpServers(List.of(OpsMcpServerConfig.builder().name("raw-node-mcp").transport("stdio").build()))
                        .build()))
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder()
                        .agentId("slow-sql-agent")
                        .mcpServers(List.of(OpsMcpServerConfig.builder().name("raw-agentscope-mcp").transport("sse").build()))
                        .build()))
                .build();

        Map<String, Object> result = fixture.service.validateBindings(definition);

        assertFalse((Boolean) result.get("valid"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.stream().anyMatch(error -> error.contains("不允许使用内联 MCP 配置")
                && error.contains("AGENT:demo-project-agent")
                && error.contains("NODE:mysql-agent")
                && error.contains("AGENTSCOPE:slow-sql-agent")));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.saveDraft(definition));
        verify(fixture.definitionRegistry, never()).saveDraft(definition);
    }

    @Test
    void shouldExtractCapabilityBindingsFromDefinitionWhenNoStoredRows() {
        Fixture fixture = fixture();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .skills(List.of("demo-project-sop"))
                .mcpIds(List.of("demo-project-mysql-prod-readonly-mcp"))
                .knowledgeBaseId("demo-ops")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("prom-agent")
                        .skills(List.of("metric-sop"))
                        .mcpIds(List.of("demo-project-prometheus-readonly-mcp"))
                        .build()))
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder()
                        .agentId("mysql-slow-agent")
                        .skills(List.of("mysql-slow-sop"))
                        .knowledgeBaseId("demo-ops")
                        .build()))
                .build();
        when(fixture.definitionRegistry.listCapabilityBindings("demo-project-agent")).thenReturn(List.of());
        when(fixture.definitionRegistry.resolve("demo-project-agent", null, true)).thenReturn(definition);

        List<Map<String, Object>> bindings = fixture.service.agentBindings("demo-project-agent");

        assertEquals(7, bindings.size());
        assertTrue(bindings.stream().anyMatch(binding -> "AGENT".equals(binding.get("ownerType"))
                && "skill".equals(binding.get("capabilityType"))
                && "demo-project-sop".equals(binding.get("capabilityId"))));
        assertTrue(bindings.stream().anyMatch(binding -> "NODE".equals(binding.get("ownerType"))
                && "prom-agent".equals(binding.get("nodeId"))
                && "demo-project-prometheus-readonly-mcp".equals(binding.get("capabilityId"))));
        assertTrue(bindings.stream().anyMatch(binding -> "AGENTSCOPE".equals(binding.get("ownerType"))
                && "mysql-slow-agent".equals(binding.get("nodeId"))
                && "mysql-slow-sop".equals(binding.get("capabilityId"))));
    }

    @Test
    void shouldUpdateCapabilityBindingsAsDraftAfterProjectValidation() {
        Fixture fixture = fixture();
        allowProject(fixture);
        when(fixture.projectSkillAuthorizationService.allows("demo-project", "demo-project-sop")).thenReturn(true);
        when(fixture.projectMcpAuthorizationService.allows("demo-project", "demo-project-mysql-prod-readonly-mcp")).thenReturn(true);
        when(fixture.projectKnowledgeAuthorizationService.allows("demo-project", "demo-ops")).thenReturn(true);
        ExecutionResource executionResource = mock(ExecutionResource.class);
        when(executionResource.status()).thenReturn(ExecutionResourceStatus.ENABLED);
        when(fixture.executionResourceService.find("demo-project", "demo-project-runtime"))
                .thenReturn(Optional.of(executionResource));
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .skills(List.of("old-skill"))
                .nodes(List.of(OpsWorkflowNode.builder().nodeId("mysql-agent").mcpIds(List.of("old-mcp")).build()))
                .build();
        when(fixture.definitionRegistry.resolve("demo-project-agent", null, true)).thenReturn(definition);
        when(fixture.definitionRegistry.listCapabilityBindings("demo-project-agent")).thenReturn(List.of());
        when(fixture.lifecycleUseCase.saveDraft(any(OpsAgentDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.updateAgentBindings("demo-project-agent", Map.of("bindings", List.of(
                Map.of("ownerType", "AGENT", "capabilityType", "skill", "capabilityId", "demo-project-sop"),
                Map.of("ownerType", "NODE", "nodeId", "mysql-agent", "capabilityType", "project_tool", "capabilityId", "demo-project-mysql-prod-readonly-mcp"),
                Map.of("ownerType", "NODE", "nodeId", "mysql-agent", "capabilityType", "knowledge_base", "capabilityId", "demo-ops"),
                Map.of("ownerType", "NODE", "nodeId", "mysql-agent", "capabilityType", "execution_target", "capabilityId", "demo-project-runtime")
        )));

        ArgumentCaptor<OpsAgentDefinition> captor = ArgumentCaptor.forClass(OpsAgentDefinition.class);
        verify(fixture.lifecycleUseCase).saveDraft(captor.capture());
        OpsAgentDefinition saved = captor.getValue();
        assertEquals(List.of("demo-project-sop"), saved.getSkills());
        assertEquals(List.of("demo-project-mysql-prod-readonly-mcp"), saved.getNodes().get(0).getMcpIds());
        assertEquals("demo-ops", saved.getNodes().get(0).getKnowledgeBaseId());
        assertEquals(List.of("demo-project-runtime"), saved.getNodes().get(0).getExecutionTargetIds());
    }

    private void allowProject(Fixture fixture) {
        when(fixture.projectDefinitionService.exists("demo-project")).thenReturn(true);
    }

    private Fixture fixture() {
        OpsAgentDefinitionGateway definitionRegistry = mock(OpsAgentDefinitionGateway.class);
        OpsConfigAuditService opsConfigAuditService = mock(OpsConfigAuditService.class);
        ProjectDefinitionApplicationService projectDefinitionService =
                mock(ProjectDefinitionApplicationService.class);
        ProjectMcpCatalogApplicationService projectMcpCatalogService =
                mock(ProjectMcpCatalogApplicationService.class);
        ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationService =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        ProjectMcpAuthorizationApplicationService projectMcpAuthorizationService =
                mock(ProjectMcpAuthorizationApplicationService.class);
        ProjectSkillAuthorizationApplicationService projectSkillAuthorizationService =
                mock(ProjectSkillAuthorizationApplicationService.class);
        ExecutionResourceQueryApplicationService executionResourceService = mock(ExecutionResourceQueryApplicationService.class);
        when(projectSkillAuthorizationService.localIds(any())).thenReturn(List.of());
        when(projectSkillAuthorizationService.globalIds(any())).thenReturn(List.of());
        when(projectMcpCatalogService.list(any())).thenReturn(List.of());
        when(projectKnowledgeAuthorizationService.projectEntries(any())).thenReturn(List.of());
        when(projectKnowledgeAuthorizationService.globalEntries(any())).thenReturn(List.of());
        when(executionResourceService.list("demo-project")).thenReturn(List.of());
        OpsAgentEvalAdapter agentEvalService = mock(OpsAgentEvalAdapter.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<OpsAgentDefinition> lifecycleUseCase =
                mock(AgentDefinitionLifecycleUseCase.class);
        OpsAgentCapabilityCatalogService capabilityCatalogService = new OpsAgentCapabilityCatalogService(
                projectDefinitionService,
                projectMcpCatalogService,
                projectKnowledgeAuthorizationService,
                projectMcpAuthorizationService,
                projectSkillAuthorizationService,
                executionResourceService);
        OpsAgentCapabilityBindingPolicy capabilityBindingPolicy = new OpsAgentCapabilityBindingPolicy(
                projectDefinitionService,
                projectKnowledgeAuthorizationService,
                projectMcpAuthorizationService,
                projectSkillAuthorizationService,
                executionResourceService);
        OpsAgentCapabilityApplicationService capabilityApplicationService = new OpsAgentCapabilityApplicationService(
                capabilityCatalogService,
                capabilityBindingPolicy,
                new OpsAgentCapabilityBindingEditor());
        OpsAgentDefinitionApplicationService service = new OpsAgentDefinitionApplicationService(
                OpsAgentDefinitionManagementAssembly.create(
                        definitionRegistry,
                        lifecycleUseCase,
                        opsConfigAuditService,
                        capabilityApplicationService,
                        agentEvalService));
        return new Fixture(service, definitionRegistry, projectDefinitionService,
                projectMcpCatalogService, projectKnowledgeAuthorizationService,
                projectMcpAuthorizationService,
                projectSkillAuthorizationService, executionResourceService, agentEvalService, lifecycleUseCase);
    }

    private record Fixture(OpsAgentDefinitionApplicationService service,
                           OpsAgentDefinitionGateway definitionRegistry,
                           ProjectDefinitionApplicationService projectDefinitionService,
                           ProjectMcpCatalogApplicationService projectMcpCatalogService,
                           ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationService,
                           ProjectMcpAuthorizationApplicationService projectMcpAuthorizationService,
                           ProjectSkillAuthorizationApplicationService projectSkillAuthorizationService,
                           ExecutionResourceQueryApplicationService executionResourceService,
                           OpsAgentEvalAdapter agentEvalService,
                           AgentDefinitionLifecycleUseCase<OpsAgentDefinition> lifecycleUseCase) {
    }
}
